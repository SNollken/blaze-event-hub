package com.nollen.blaze.overlays;

import java.sql.Blob;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOverlayAssetStorage implements OverlayAssetStorage {

	private final ConcurrentHashMap<String, byte[]> bytesByAssetId = new ConcurrentHashMap<>();
	private final JdbcTemplate jdbc;

	/** RowMapper that reads a BLOB column and converts it to byte[]. */
	private static final RowMapper<byte[]> BLOB_ROW_MAPPER = (ResultSet rs, int rowNum) -> {
		Blob blob = rs.getBlob("asset_bytes");
		if (blob == null) {
			return null;
		}
		try {
			return blob.getBytes(1, (int) blob.length());
		}
		finally {
			blob.free();
		}
	};

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
			int updated = jdbc.update("UPDATE overlay_asset_bytes SET asset_bytes = ? WHERE asset_id = ?", bytes.clone(), assetId);
		if (updated == 0) {
			jdbc.update("INSERT INTO overlay_asset_bytes (asset_bytes, asset_id) VALUES (?, ?)", bytes.clone(), assetId);
		}
		return;
		}
		bytesByAssetId.put(assetId, bytes.clone());
	}

	@Override
	public Optional<byte[]> read(String assetId) {
		if (jdbc != null) {
			try {
				byte[] bytes = jdbc.queryForObject(
						"SELECT asset_bytes FROM overlay_asset_bytes WHERE asset_id = ?",
						BLOB_ROW_MAPPER, assetId);
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
