package top.ellan.mahjong.gb.jni;

public final class GbMahjongNativeBridge {
    public boolean isAvailable() {
        return GbMahjongNativeLibrary.ensureLoaded().available();
    }

    public String availabilityDetail() {
        return GbMahjongNativeLibrary.ensureLoaded().detail();
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
        GbMahjongNativeLibrary.LoadState state = GbMahjongNativeLibrary.ensureLoaded();
        if (!state.available()) {
            throw new IllegalStateException(state.detail());
        }
    }

    private static native String nativeLibraryVersion();

    private static native String nativePing();

    private static native String nativeEvaluateFan(String requestJson);

    private static native String nativeEvaluateTing(String requestJson);

    private static native String nativeEvaluateWin(String requestJson);
}

