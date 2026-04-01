package top.ellan.mahjong.gb.runtime;

import top.ellan.mahjong.error.MahjongErrorCode;
import top.ellan.mahjong.error.MahjongInfrastructureException;
import top.ellan.mahjong.gb.jni.GbFanRequest;
import top.ellan.mahjong.gb.jni.GbFanResponse;
import top.ellan.mahjong.gb.jni.GbMahjongNativeBridge;
import top.ellan.mahjong.gb.jni.GbMeldInput;
import top.ellan.mahjong.gb.jni.GbSeatPointsInput;
import top.ellan.mahjong.gb.jni.GbTingRequest;
import top.ellan.mahjong.gb.jni.GbTingResponse;
import top.ellan.mahjong.gb.jni.GbWinRequest;
import top.ellan.mahjong.gb.jni.GbWinResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class GbNativeRulesGateway {
    private static final int DEFAULT_TING_CACHE_SIZE = 2048;
    private static final int DEFAULT_WIN_CACHE_SIZE = 1024;
    private static final Logger LOGGER = Logger.getLogger(GbNativeRulesGateway.class.getName());

    private final GbMahjongNativeBridge bridge = new GbMahjongNativeBridge();
    private final Object tingCacheLock = new Object();
    private final Object winCacheLock = new Object();
    private final Map<Long, GbTingResponse> tingCache;
    private final Map<Long, GbWinResponse> winCache;

    public GbNativeRulesGateway() {
        this(DEFAULT_TING_CACHE_SIZE, DEFAULT_WIN_CACHE_SIZE);
    }

    public GbNativeRulesGateway(int tingCacheSize, int winCacheSize) {
        this.tingCache = createLruCache(Math.max(0, tingCacheSize));
        this.winCache = createLruCache(Math.max(0, winCacheSize));
    }

    public boolean isAvailable() {
        return this.bridge.isAvailable();
    }

    public GbFanResponse evaluateFan(GbFanRequest request) {
        if (request == null || !this.isAvailable()) {
            return invalidFan(MahjongErrorCode.GB_NATIVE_BRIDGE_UNAVAILABLE.publicMessage());
        }
        try {
            GbFanResponse response = this.bridge.evaluateFan(request);
            return response == null
                ? invalidFan(MahjongErrorCode.GB_NATIVE_FAN_EVALUATION_FAILED.publicMessage())
                : response;
        } catch (RuntimeException ex) {
            MahjongInfrastructureException failure = new MahjongInfrastructureException(
                MahjongErrorCode.GB_NATIVE_FAN_EVALUATION_FAILED,
                MahjongErrorCode.GB_NATIVE_FAN_EVALUATION_FAILED.publicMessage(),
                ex
            );
            LOGGER.log(failure.logLevel(), failure.code().name(), ex);
            return invalidFan(failure.publicMessage());
        }
    }

    public GbTingResponse evaluateTing(GbTingRequest request) {
        if (request == null || !this.isAvailable()) {
            return invalidTing(MahjongErrorCode.GB_NATIVE_BRIDGE_UNAVAILABLE.publicMessage());
        }
        long cacheKey = hashTingRequest(request);
        GbTingResponse cached = this.cachedTing(cacheKey);
        if (cached != null) {
            return cached;
        }
        GbTingResponse response;
        try {
            response = this.evaluateTingNative(request);
            if (response == null) {
                response = invalidTing(MahjongErrorCode.GB_NATIVE_TING_EVALUATION_FAILED.publicMessage());
            }
        } catch (RuntimeException ex) {
            MahjongInfrastructureException failure = new MahjongInfrastructureException(
                MahjongErrorCode.GB_NATIVE_TING_EVALUATION_FAILED,
                MahjongErrorCode.GB_NATIVE_TING_EVALUATION_FAILED.publicMessage(),
                ex
            );
            LOGGER.log(failure.logLevel(), failure.code().name(), ex);
            response = invalidTing(failure.publicMessage());
        }
        this.cacheTing(cacheKey, response);
        return response;
    }

    public GbWinResponse evaluateWin(GbWinRequest request) {
        if (request == null || !this.isAvailable()) {
            return invalidWin(MahjongErrorCode.GB_NATIVE_BRIDGE_UNAVAILABLE.publicMessage());
        }
        long cacheKey = hashWinRequest(request);
        GbWinResponse cached = this.cachedWin(cacheKey);
        if (cached != null) {
            return cached;
        }
        GbWinResponse response;
        try {
            response = this.evaluateWinNative(request);
            if (response == null) {
                response = invalidWin(MahjongErrorCode.GB_NATIVE_WIN_EVALUATION_FAILED.publicMessage());
            }
        } catch (RuntimeException ex) {
            MahjongInfrastructureException failure = new MahjongInfrastructureException(
                MahjongErrorCode.GB_NATIVE_WIN_EVALUATION_FAILED,
                MahjongErrorCode.GB_NATIVE_WIN_EVALUATION_FAILED.publicMessage(),
                ex
            );
            LOGGER.log(failure.logLevel(), failure.code().name(), ex);
            response = invalidWin(failure.publicMessage());
        }
        this.cacheWin(cacheKey, response);
        return response;
    }

    protected GbTingResponse evaluateTingNative(GbTingRequest request) {
        return this.bridge.evaluateTing(request);
    }

    protected GbWinResponse evaluateWinNative(GbWinRequest request) {
        return this.bridge.evaluateWin(request);
    }

    private static GbFanResponse invalidFan(String message) {
        return new GbFanResponse(false, 0, List.of(), message);
    }

    private static GbTingResponse invalidTing(String message) {
        return new GbTingResponse(false, List.of(), message);
    }

    private static GbWinResponse invalidWin(String message) {
        return new GbWinResponse(false, "WIN", 0, List.of(), List.of(), message);
    }

    private GbTingResponse cachedTing(long cacheKey) {
        synchronized (this.tingCacheLock) {
            return this.tingCache.get(cacheKey);
        }
    }

    private void cacheTing(long cacheKey, GbTingResponse response) {
        synchronized (this.tingCacheLock) {
            this.tingCache.put(cacheKey, response);
        }
    }

    private GbWinResponse cachedWin(long cacheKey) {
        synchronized (this.winCacheLock) {
            return this.winCache.get(cacheKey);
        }
    }

    private void cacheWin(long cacheKey, GbWinResponse response) {
        synchronized (this.winCacheLock) {
            this.winCache.put(cacheKey, response);
        }
    }

    private static <T> Map<Long, T> createLruCache(int maxEntries) {
        return new LinkedHashMap<>(Math.max(16, maxEntries), 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, T> eldest) {
                return this.size() > maxEntries;
            }
        };
    }

    private static long hashTingRequest(GbTingRequest request) {
        RequestFingerprint fingerprint = new RequestFingerprint();
        fingerprint.field(request.getRuleProfile());
        hashStringList(fingerprint, request.getHandTiles());
        hashMelds(fingerprint, request.getMelds());
        fingerprint.field(request.getSeatWind());
        fingerprint.field(request.getRoundWind());
        hashStringList(fingerprint, request.getFlowerTiles());
        hashStringList(fingerprint, request.getFlags());
        return fingerprint.value();
    }

    private static long hashWinRequest(GbWinRequest request) {
        RequestFingerprint fingerprint = new RequestFingerprint();
        fingerprint.field(request.getRuleProfile());
        hashStringList(fingerprint, request.getHandTiles());
        hashMelds(fingerprint, request.getMelds());
        fingerprint.field(request.getWinningTile());
        fingerprint.field(request.getWinType());
        fingerprint.field(request.getWinnerSeat());
        fingerprint.field(request.getDiscarderSeat());
        fingerprint.field(request.getSeatWind());
        fingerprint.field(request.getRoundWind());
        hashSeatPoints(fingerprint, request.getSeatPoints());
        hashStringList(fingerprint, request.getFlowerTiles());
        hashStringList(fingerprint, request.getFlags());
        return fingerprint.value();
    }

    private static void hashMelds(RequestFingerprint fingerprint, List<GbMeldInput> melds) {
        fingerprint.field(melds.size());
        for (GbMeldInput meld : melds) {
            fingerprint.field(meld.getType());
            hashStringList(fingerprint, meld.getTiles());
            fingerprint.field(meld.getClaimedTile());
            fingerprint.field(meld.getFromSeat());
            fingerprint.field(meld.getOpen());
        }
    }

    private static void hashSeatPoints(RequestFingerprint fingerprint, List<GbSeatPointsInput> seatPoints) {
        fingerprint.field(seatPoints.size());
        for (GbSeatPointsInput seatPoint : seatPoints) {
            fingerprint.field(seatPoint.getSeat());
            fingerprint.field(seatPoint.getPoints());
        }
    }

    private static void hashStringList(RequestFingerprint fingerprint, List<String> values) {
        fingerprint.field(values.size());
        for (String value : values) {
            fingerprint.field(value);
        }
    }

    private static final class RequestFingerprint {
        private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
        private static final long FNV_PRIME = 0x100000001b3L;

        private long hash = FNV_OFFSET_BASIS;
        private boolean needsSeparator;

        private void field(Object value) {
            if (this.needsSeparator) {
                this.mix('\u001f');
            }
            String text = Objects.toString(value, "");
            for (int index = 0; index < text.length(); index++) {
                this.mix(text.charAt(index));
            }
            this.needsSeparator = true;
        }

        private long value() {
            return this.hash;
        }

        private void mix(char value) {
            this.hash ^= value;
            this.hash *= FNV_PRIME;
        }
    }
}

