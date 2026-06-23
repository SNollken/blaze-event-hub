package com.nollen.blaze.overlays;

import java.util.Optional;

public interface OverlayAssetStorage {

	void store(String assetId, byte[] bytes);

	Optional<byte[]> read(String assetId);

	void delete(String assetId);
}
