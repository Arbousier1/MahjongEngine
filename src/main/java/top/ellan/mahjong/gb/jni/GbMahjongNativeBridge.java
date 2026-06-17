package top.ellan.mahjong.gb.jni;

public final class GbMahjongNativeBridge {
    private volatile GbMahjongNativeLibrary.LoadState loadState;

    public boolean isAvailable() {
        return this.loadState().available();
    }

    public String availabilityDetail() {
        return this.loadState().detail();
    }

    public String libraryVersion() {
        requireAvailable();
        return nativeLibraryVersion();
    }

    public String ping() {
        requireAvailable();
        return nativePing();
    }

    public GbFanResponse evaluateFan(GbFanRequest request) {
        requireAvailable();
        String payload = GbMahjongNativeJson.encodeFanRequest(request);
        return GbMahjongNativeJson.decodeFanResponse(nativeEvaluateFan(payload));
    }

    public GbTingResponse evaluateTing(GbTingRequest request) {
        requireAvailable();
        String payload = GbMahjongNativeJson.encodeTingRequest(request);
        return GbMahjongNativeJson.decodeTingResponse(nativeEvaluateTing(payload));
    }

    public GbWinResponse evaluateWin(GbWinRequest request) {
        requireAvailable();
        String payload = GbMahjongNativeJson.encodeWinRequest(request);
        return GbMahjongNativeJson.decodeWinResponse(nativeEvaluateWin(payload));
    }

    private void requireAvailable() {
        GbMahjongNativeLibrary.LoadState state = this.loadState();
        if (!state.available()) {
            throw new IllegalStateException(state.detail());
        }
    }

    private GbMahjongNativeLibrary.LoadState loadState() {
        GbMahjongNativeLibrary.LoadState cached = this.loadState;
        if (cached != null) {
            return cached;
        }
        GbMahjongNativeLibrary.LoadState resolved = GbMahjongNativeLibrary.ensureLoaded();
        this.loadState = resolved;
        return resolved;
    }

    private static native String nativeLibraryVersion();

    private static native String nativePing();

    private static native String nativeEvaluateFan(String requestJson);

    private static native String nativeEvaluateTing(String requestJson);

    private static native String nativeEvaluateWin(String requestJson);
}

