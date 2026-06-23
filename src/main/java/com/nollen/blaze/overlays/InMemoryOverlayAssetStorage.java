package com.nollen.blaze.overlays;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOverlayAssetStorage implements OverlayAssetStorage {

	private final ConcurrentHashMap<String, byte[]> bytesByAssetId = new ConcurrentHashMap<>();

	@Override
	public void store(String assetId, byte[] bytes) {
		bytesByAssetId.put(assetId, bytes.clone());
	}

	@Override
	public Optional<byte[]> read(String assetId) {
		return Optional.ofNullable(bytesByAssetId.get(assetId)).map(byte[]::clone);
	}

	@Override
	public void delete(String assetId) {
		bytesByAssetId.remove(assetId);
	}
}
