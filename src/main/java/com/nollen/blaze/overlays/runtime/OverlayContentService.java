package com.nollen.blaze.overlays.runtime;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.List;
import java.util.ArrayList;

import org.springframework.stereotype.Service;

@Service
public class OverlayContentService {

	private static final int MAX_EVENTS = 50;
	private final ConcurrentLinkedDeque<Map<String, Object>> recentAlerts = new ConcurrentLinkedDeque<>();
	private final ConcurrentLinkedDeque<Map<String, Object>> recentGiveaways = new ConcurrentLinkedDeque<>();
	private final ConcurrentLinkedDeque<Map<String, Object>> recentEvents = new ConcurrentLinkedDeque<>();

	public void addAlert(Map<String, Object> alertData) {
		Map<String, Object> event = Map.of(
				"id", java.util.UUID.randomUUID().toString(),
				"timestamp", Instant.now().toString(),
				"data", alertData);
		recentAlerts.addFirst(event);
		trim(recentAlerts);
	}

	public void addGiveaway(Map<String, Object> giveawayData) {
		Map<String, Object> event = Map.of(
				"id", java.util.UUID.randomUUID().toString(),
				"timestamp", Instant.now().toString(),
				"data", giveawayData);
		recentGiveaways.addFirst(event);
		trim(recentGiveaways);
	}

	public void addEvent(Map<String, Object> eventData) {
		Map<String, Object> event = Map.of(
				"id", java.util.UUID.randomUUID().toString(),
				"timestamp", Instant.now().toString(),
				"data", eventData);
		recentEvents.addFirst(event);
		trim(recentEvents);
	}

	public List<Map<String, Object>> getRecentAlerts() {
		return new ArrayList<>(recentAlerts);
	}

	public List<Map<String, Object>> getRecentGiveaways() {
		return new ArrayList<>(recentGiveaways);
	}

	public List<Map<String, Object>> getRecentEvents() {
		return new ArrayList<>(recentEvents);
	}

	public String generateAlertHtml(RuntimeOverlayConfig config) {
		return generateHtml(config, "alert");
	}

	public String generateGiveawayHtml(RuntimeOverlayConfig config) {
		return generateHtml(config, "giveaway");
	}

	public String generateEventsHtml(RuntimeOverlayConfig config) {
		return generateHtml(config, "events");
	}

	private String generateHtml(RuntimeOverlayConfig config, String overlayType) {
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n");
		html.append("<meta charset=\"utf-8\">\n");
		html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
		html.append("<meta name=\"nollenblaze-overlay-runtime\" content=\"overlay runtime\">\n");
		html.append("<title>NollenBlaze Overlay - ").append(escapeHtml(config.name())).append("</title>\n");
		html.append("<style>\n");
		html.append(getBaseCss());
		html.append(getOverlayTypeCss(overlayType));
		if (config.customCss() != null && !config.customCss().isBlank()) {
			html.append("\n/* Custom CSS */\n").append(config.customCss());
		}
		html.append("\n</style>\n");
		html.append("</head>\n<body>\n");
		html.append("<div class=\"overlay-container\" data-overlay-type=\"").append(overlayType).append("\" ");
		html.append("data-refresh-ms=\"").append(config.refreshIntervalMs()).append("\">\n");
		html.append("<div id=\"overlay-content\" class=\"overlay-content\"></div>\n");
		html.append("</div>\n");
		html.append("<script>\n");
		html.append(getOverlayTypeScript(overlayType));
		html.append("\n</script>\n");
		html.append("</body>\n</html>");
		return html.toString();
	}

	private String getBaseCss() {
		return """
				:root { color-scheme: dark; font-family: 'Segoe UI', Arial, sans-serif; }
				* { box-sizing: border-box; margin: 0; padding: 0; }
				html, body { width: 100%; height: 100%; overflow: hidden; background: transparent; }
				body { min-width: 320px; }
				.overlay-container { position: fixed; inset: 0; pointer-events: none; user-select: none; }
				""";
	}

	private String getOverlayTypeCss(String overlayType) {
		return switch (overlayType) {
			case "alert" -> getAlertCss();
			case "giveaway" -> getGiveawayCss();
			case "events" -> getEventsCss();
			default -> "";
		};
	}

	private String getAlertCss() {
		return """
				.alert-item {
				  position: absolute; bottom: 20px; left: 50%;
				  transform: translateX(-50%);
				  background: linear-gradient(135deg, rgba(220, 38, 38, 0.95), rgba(185, 28, 28, 0.95));
				  color: #fff; padding: 16px 24px; border-radius: 12px;
				  font-size: 18px; font-weight: 600; text-align: center;
				  min-width: 280px; max-width: 500px;
				  box-shadow: 0 8px 32px rgba(220, 38, 38, 0.4);
				  animation: alertPopIn 0.4s cubic-bezier(0.34, 1.56, 0.64, 1) forwards,
				             alertFadeOut 0.5s ease-in 4.5s forwards;
				  z-index: 1000; pointer-events: none;
				}
				@keyframes alertPopIn {
				  0% { opacity: 0; transform: translateX(-50%) scale(0.3) translateY(40px); }
				  100% { opacity: 1; transform: translateX(-50%) scale(1) translateY(0); }
				}
				@keyframes alertFadeOut {
				  0% { opacity: 1; }
				  100% { opacity: 0; transform: translateX(-50%) scale(0.8) translateY(-20px); }
				}
				""";
	}

	private String getGiveawayCss() {
		return """
				.giveaway-container {
				  position: absolute; top: 50%; left: 50%;
				  transform: translate(-50%, -50%);
				  text-align: center; color: #fff;
				}
				.giveaway-title {
				  font-size: 28px; font-weight: 700;
				  color: #fbbf24; text-shadow: 0 2px 8px rgba(251, 191, 36, 0.5);
				  margin-bottom: 12px;
				}
				.giveaway-participants {
				  font-size: 16px; color: #d1d5db; margin-bottom: 8px;
				}
				.giveaway-winner {
				  font-size: 32px; font-weight: 800;
				  background: linear-gradient(90deg, #fbbf24, #f59e0b, #fbbf24);
				  background-size: 200% auto;
				  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
				  animation: goldShimmer 2s linear infinite, winnerReveal 0.6s ease-out;
				  filter: drop-shadow(0 0 12px rgba(251, 191, 36, 0.6));
				}
				@keyframes goldShimmer {
				  0% { background-position: 0% center; }
				  100% { background-position: 200% center; }
				}
				@keyframes winnerReveal {
				  0% { opacity: 0; transform: scale(0.5) rotate(-10deg); }
				  60% { transform: scale(1.1) rotate(2deg); }
				  100% { opacity: 1; transform: scale(1) rotate(0deg); }
				}
				.giveaway-spinning {
				  font-size: 20px; color: #9ca3af;
				  animation: spin 1s linear infinite;
				}
				@keyframes spin {
				  0% { transform: rotate(0deg); display: inline-block; }
				  100% { transform: rotate(360deg); display: inline-block; }
				}
				""";
	}

	private String getEventsCss() {
		return """
				.events-feed {
				  position: absolute; top: 10px; right: 10px;
				  max-height: calc(100vh - 20px); overflow: hidden;
				  display: flex; flex-direction: column; gap: 6px;
				  width: 360px;
				}
				.event-item {
				  padding: 10px 14px; border-radius: 8px; font-size: 14px;
				  color: #fff; backdrop-filter: blur(8px);
				  animation: eventSlideIn 0.3s ease-out forwards;
				  opacity: 0; transform: translateX(40px);
				}
				@keyframes eventSlideIn {
				  to { opacity: 1; transform: translateX(0); }
				}
				.event-item.type-FOLLOW { background: rgba(34, 197, 94, 0.85); border-left: 4px solid #22c55e; }
				.event-item.type-SUBSCRIBE { background: rgba(147, 51, 234, 0.85); border-left: 4px solid #9333ea; }
				.event-item.type-GIFT { background: rgba(251, 191, 36, 0.85); border-left: 4px solid #fbbf24; }
				.event-item.type-DONATION { background: rgba(239, 68, 68, 0.85); border-left: 4px solid #ef4444; }
				.event-item.type-RAID { background: rgba(6, 182, 212, 0.85); border-left: 4px solid #06b6d4; }
				.event-item.type-CHAT { background: rgba(107, 114, 128, 0.7); border-left: 4px solid #6b7280; }
				.event-item.type-DEFAULT { background: rgba(75, 85, 99, 0.7); border-left: 4px solid #4b5563; }
				.event-time { font-size: 11px; opacity: 0.7; margin-top: 4px; }
				.event-label { font-weight: 600; }
				""";
	}

	private String getOverlayTypeScript(String overlayType) {
		return switch (overlayType) {
			case "alert" -> getAlertScript();
			case "giveaway" -> getGiveawayScript();
			case "events" -> getEventsScript();
			default -> "";
		};
	}

	private String getAlertScript() {
		return """
				(function() {
				  var container = document.querySelector('.overlay-container');
				  var refreshMs = parseInt(container.dataset.refreshMs || '3000', 10);
				  var content = document.getElementById('overlay-content');
				  var seenIds = {};

				  function checkAlerts() {
				    fetch('/api/overlays/content/alerts', {cache: 'no-store'})
				      .then(function(r) { return r.json(); })
				      .then(function(alerts) {
				        alerts.forEach(function(alert) {
				          if (!seenIds[alert.id]) {
				            seenIds[alert.id] = true;
				            showAlert(alert);
				          }
				        });
				      })
				      .catch(function() {});
				  }

				  function showAlert(alert) {
				    var data = alert.data || {};
				    var el = document.createElement('div');
				    el.className = 'alert-item';
				    el.textContent = data.message || data.title || 'New Alert!';
				    content.appendChild(el);
				    setTimeout(function() { el.remove(); }, 5500);
				  }

				  setInterval(checkAlerts, refreshMs);
				  checkAlerts();
				})();
				""";
	}

	private String getGiveawayScript() {
		return """
				(function() {
				  var container = document.querySelector('.overlay-container');
				  var refreshMs = parseInt(container.dataset.refreshMs || '3000', 10);
				  var content = document.getElementById('overlay-content');

				  function updateGiveaway() {
				    fetch('/api/overlays/content/giveaways', {cache: 'no-store'})
				      .then(function(r) { return r.json(); })
				      .then(function(giveaways) {
				        if (!giveaways.length) {
				          content.innerHTML = '<div class="giveaway-container"><div class="giveaway-title">No Active Giveaway</div></div>';
				          return;
				        }
				        var latest = giveaways[0];
				        var data = latest.data || {};
				        if (data.winner) {
				          content.innerHTML = '<div class="giveaway-container"><div class="giveaway-title">' + (data.title || 'Giveaway') + '</div><div class="giveaway-winner">' + data.winner + '</div></div>';
				        } else {
				          content.innerHTML = '<div class="giveaway-container"><div class="giveaway-title">' + (data.title || 'Giveaway') + '</div><div class="giveaway-participants">' + (data.participants || 0) + ' participants</div><div class="giveaway-spinning">\u2728</div></div>';
				        }
				      })
				      .catch(function() {});
				  }

				  setInterval(updateGiveaway, refreshMs);
				  updateGiveaway();
				})();
				""";
	}

	private String getEventsScript() {
		return """
				(function() {
				  var container = document.querySelector('.overlay-container');
				  var refreshMs = parseInt(container.dataset.refreshMs || '3000', 10);
				  var content = document.getElementById('overlay-content');
				  var feed = document.createElement('div');
				  feed.className = 'events-feed';
				  content.appendChild(feed);

				  function updateEvents() {
				    fetch('/api/overlays/content/events', {cache: 'no-store'})
				      .then(function(r) { return r.json(); })
				      .then(function(events) {
				        feed.innerHTML = '';
				        events.slice(0, 15).forEach(function(ev) {
				          var data = ev.data || {};
				          var el = document.createElement('div');
				          var eventType = (data.eventType || 'DEFAULT').toUpperCase();
				          el.className = 'event-item type-' + eventType;
				          el.innerHTML = '<div class="event-label">' + escapeHtml(data.message || data.title || eventType) + '</div>' +
				              '<div class="event-time">' + new Date(ev.timestamp).toLocaleTimeString() + '</div>';
				          feed.appendChild(el);
				        });
				      })
				      .catch(function() {});
				  }

				  function escapeHtml(text) {
				    var div = document.createElement('div');
				    div.textContent = text;
				    return div.innerHTML;
				  }

				  setInterval(updateEvents, refreshMs);
				  updateEvents();
				})();
				""";
	}

	private static void trim(ConcurrentLinkedDeque<?> deque) {
		while (deque.size() > MAX_EVENTS) {
			deque.removeLast();
		}
	}

	private static String escapeHtml(String input) {
		if (input == null) return "";
		return input.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
}
