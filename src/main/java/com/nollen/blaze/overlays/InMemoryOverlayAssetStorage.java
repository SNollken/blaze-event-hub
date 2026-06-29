package com.nollen.blaze.overlays;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOverlayAssetStorage implements OverlayAssetStorage {

	private final ConcurrentHashMap<String, byte[]> bytesByAssetId = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;

	public InMemoryOverlayAssetStorage() {
		this.jdbc = null;
	}

	@Autowired
	public InMemoryOverlayAssetStorage(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void store(String assetId, byte[] bytes) {
		if (jdbc != null) {
			jdbc.update("MERGE INTO overlay_asset_bytes KEY(asset_id) VALUES (?, ?)", assetId, bytes.clone());
			return;
		}
		bytesByAssetId.put(assetId, bytes.clone());
	}

	@Override
	public Optional<byte[]> read(String assetId) {
		if (jdbc != null) {
			try {
				byte[] bytes = jdbc.queryForObject("SELECT asset_bytes FROM overlay_asset_bytes WHERE asset_id = ?",
						byte[].class, assetId);
				return Optional.ofNullable(bytes).map(byte[]::clone);
			}
			catch (EmptyResultDataAccessException ex) {
				return Optional.empty();
			}
		}
		return Optional.ofNullable(bytesByAssetId.get(assetId)).map(byte[]::clone);
	}

	@Override
	public void delete(String assetId) {
		if (jdbc != null) {
			jdbc.update("DELETE FROM overlay_asset_bytes WHERE asset_id = ?", assetId);
			return;
		}
		bytesByAssetId.remove(assetId);
	}
}
