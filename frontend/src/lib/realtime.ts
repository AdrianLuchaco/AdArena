import { Client } from "@stomp/stompjs";
import { getFreshAccessToken } from "./api";
import { WS_URL } from "./config";
import type { ArenaNotification, HomeData } from "./types";

interface ArenaConnectionOptions {
  /** Hay sesión: además del canal público, escuchar los avisos privados. */
  authenticated: boolean;
  onArena: (home: HomeData) => void;
  onNotification: (notification: ArenaNotification) => void;
  onStatusChange: (connected: boolean) => void;
}

/**
 * Conexión en tiempo real con la API (WebSocket + STOMP).
 *  - /topic/arena: portada y clasificación cada vez que alguien puja.
 *  - /user/queue/notifications: avisos privados ("te han superado").
 * Si se corta, se reconecta sola a los 3 segundos, con un token recién renovado.
 */
export function connectArena({ authenticated, onArena, onNotification, onStatusChange }: ArenaConnectionOptions): Client {
  let sentToken = false;

  const client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 3000,
    beforeConnect: async (stomp) => {
      const token = authenticated ? await getFreshAccessToken() : null;
      sentToken = Boolean(token);
      stomp.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    },
    onConnect: () => {
      onStatusChange(true);
      client.subscribe("/topic/arena", (message) => onArena(JSON.parse(message.body) as HomeData));
      if (sentToken) {
        client.subscribe("/user/queue/notifications", (message) =>
          onNotification(JSON.parse(message.body) as ArenaNotification),
        );
      }
    },
    onWebSocketClose: () => onStatusChange(false),
    onStompError: () => onStatusChange(false),
  });

  client.activate();
  return client;
}
