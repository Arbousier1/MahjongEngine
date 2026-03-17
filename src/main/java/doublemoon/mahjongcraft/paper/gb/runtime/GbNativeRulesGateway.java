package doublemoon.mahjongcraft.paper.gb.runtime;

import doublemoon.mahjongcraft.paper.gb.jni.GbFanRequest;
import doublemoon.mahjongcraft.paper.gb.jni.GbFanResponse;
import doublemoon.mahjongcraft.paper.gb.jni.GbMahjongNativeBridge;
import doublemoon.mahjongcraft.paper.gb.jni.GbTingRequest;
import doublemoon.mahjongcraft.paper.gb.jni.GbTingResponse;
import doublemoon.mahjongcraft.paper.gb.jni.GbWinRequest;
import doublemoon.mahjongcraft.paper.gb.jni.GbWinResponse;
import java.util.List;
import java.util.Objects;

public class GbNativeRulesGateway {
    private final GbMahjongNativeBridge bridge = new GbMahjongNativeBridge();

    public boolean isAvailable() {
        return this.bridge.isAvailable();
    }

    public GbFanResponse evaluateFan(GbFanRequest request) {
        if (request == null || !this.isAvailable()) {
            return invalidFan("GB Mahjong native bridge is unavailable.");
        }
        try {
            GbFanResponse response = this.bridge.evaluateFan(request);
            return response == null ? invalidFan("GB Mahjong native bridge returned no fan response.") : response;
        } catch (RuntimeException ex) {
            return invalidFan(Objects.toString(ex.getMessage(), "GB Mahjong native fan evaluation failed."));
        }
    }

    public GbTingResponse evaluateTing(GbTingRequest request) {
        if (request == null || !this.isAvailable()) {
            return invalidTing("GB Mahjong native bridge is unavailable.");
        }
        try {
            GbTingResponse response = this.bridge.evaluateTing(request);
            return response == null ? invalidTing("GB Mahjong native bridge returned no ting response.") : response;
        } catch (RuntimeException ex) {
            return invalidTing(Objects.toString(ex.getMessage(), "GB Mahjong native ting evaluation failed."));
        }
    }

    public GbWinResponse evaluateWin(GbWinRequest request) {
        if (request == null || !this.isAvailable()) {
            return invalidWin("GB Mahjong native bridge is unavailable.");
        }
        try {
            GbWinResponse response = this.bridge.evaluateWin(request);
            return response == null ? invalidWin("GB Mahjong native bridge returned no win response.") : response;
        } catch (RuntimeException ex) {
            return invalidWin(Objects.toString(ex.getMessage(), "GB Mahjong native win evaluation failed."));
        }
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
}
