import { useCallback, useEffect, useMemo, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { alertsApi, API_URL } from '../lib/api';
import { AlertsContext, useAuth, useToast } from './contexts';

/**
 * Keeps a live connection to the server for air-quality alerts on the user's
 * watched routes, plus the unread badge count. Alerts are also stored on the
 * server, so anything missed while offline appears on the Alerts page.
 */
export default function AlertsProvider({ children }) {
  const { token } = useAuth();
  const { push } = useToast();
  const [unread, setUnread] = useState(0);
  const [connected, setConnected] = useState(false);
  const [lastAlert, setLastAlert] = useState(null);

  const refreshUnread = useCallback(() => {
    alertsApi.unreadCount().then(setUnread).catch(() => {});
  }, []);

  useEffect(() => {
    if (!token) return undefined;
    refreshUnread();

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_URL}/ws`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 10000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      onConnect: () => {
        setConnected(true);
        // Catch up on anything that arrived while disconnected.
        refreshUnread();
        client.subscribe('/user/queue/alerts', (message) => {
          let alert;
          try {
            alert = JSON.parse(message.body);
          } catch {
            return;
          }
          setLastAlert(alert);
          setUnread((n) => n + 1);
          const poor = alert.kind === 'POOR_AIR';
          push({
            tone: poor ? 'poor' : 'clean',
            title: poor ? `Poor air on ${alert.routeName ?? 'a watched route'}` : `Cleaner air on ${alert.routeName ?? 'a watched route'}`,
            body: alert.message,
            link: alert.routeId ? { to: `/routes/${alert.routeId}`, label: 'View route' } : undefined,
            duration: 10000,
          });
        });
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    client.activate();
    return () => {
      setConnected(false);
      client.deactivate();
    };
  }, [token, push, refreshUnread]);

  const value = useMemo(
    () => ({ unread, setUnread, refreshUnread, connected, lastAlert }),
    [unread, refreshUnread, connected, lastAlert],
  );

  return <AlertsContext.Provider value={value}>{children}</AlertsContext.Provider>;
}
