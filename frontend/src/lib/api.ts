import { apiUrl } from "./config";
import type {
  AdminAdSlot,
  AdminOverview,
  AdminTask,
  AdProfile,
  AdProfileInput,
  ArenaSettings,
  AuditEntry,
  AuthResponse,
  BidResponse,
  EarnOverview,
  HistoryPage,
  HomeData,
  MyArenaStatus,
  MyBid,
  NotificationsResponse,
  PointsOverview,
  ProblemBody,
  Promotion,
  PromotionsOverview,
  PublicProject,
  TaskClaimed,
  TasksOverview,
  TaskStarted,
  TickResult,
  UploadedImage,
  User,
  ViewStatus,
  WalletBalance,
  MyShowcase,
} from "./types";

/**
 * Cliente de la API de AdArena.
 *
 * Sesión:
 *  - El access token (15 min) vive SOLO en memoria (esta variable). Nunca en localStorage.
 *  - El refresh token vive en una cookie HttpOnly que gestiona el navegador.
 *  - Si una llamada responde 401 porque el token caducó, renovamos la sesión y repetimos la
 *    llamada una vez, sin que el usuario note nada.
 */

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

const NETWORK_ERROR_MESSAGE = "We can't reach AdArena. Check your connection and try again.";
const REFRESH_MARGIN_MS = 30_000;

let accessToken: string | null = null;
let accessTokenExpiresAt = 0;
let refreshInFlight: Promise<AuthResponse | null> | null = null;

type SessionListener = (user: User | null) => void;
const sessionListeners = new Set<SessionListener>();

/** Permite a la interfaz enterarse de cuándo se entra o se sale (p. ej. si la sesión caduca). */
export function onSessionChange(listener: SessionListener): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

/** Token actual (para el WebSocket). Lo renueva antes si está a punto de caducar. */
export async function getFreshAccessToken(): Promise<string | null> {
  if (accessToken && accessTokenExpiresAt - Date.now() < REFRESH_MARGIN_MS) {
    await refreshSession();
  }
  return accessToken;
}

function setSession(auth: AuthResponse | null) {
  accessToken = auth?.accessToken ?? null;
  accessTokenExpiresAt = auth ? new Date(auth.expiresAt).getTime() : 0;
  sessionListeners.forEach((listener) => listener(auth?.user ?? null));
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: ProblemBody | null = null;
  try {
    body = (await response.json()) as ProblemBody;
  } catch {
    // Respuesta sin JSON (p. ej. un proxy caído)
  }
  return new ApiError(
    response.status,
    body?.code ?? `HTTP_${response.status}`,
    body?.detail ?? "Something went wrong. Please try again.",
    body?.errors ?? {},
  );
}

async function send(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(apiUrl(path), init);
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", NETWORK_ERROR_MESSAGE);
  }
}

/**
 * Renueva la sesión con la cookie. Si varias partes de la web lo piden a la vez, se hace UNA
 * sola petición y todas esperan el mismo resultado (evita "carreras" entre renovaciones).
 */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function doRefresh(retryOnRace = true): Promise<AuthResponse | null> {
  const response = await send("/api/auth/refresh", { method: "POST", credentials: "include" });
  if (response.ok) {
    const auth = (await response.json()) as AuthResponse;
    setSession(auth);
    return auth;
  }
  const error = await toApiError(response);
  if (error.code === "REFRESH_TOKEN_RACE" && retryOnRace) {
    // Otra pestaña acaba de renovar: la cookie nueva ya está en el navegador
    await new Promise((resolve) => setTimeout(resolve, 300));
    return doRefresh(false);
  }
  if (error.status === 401 || error.status === 403) {
    setSession(null);
    return null;
  }
  throw error;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  formData?: FormData;
  auth?: boolean;
  headers?: Record<string, string>;
}

async function request<T>(
  path: string,
  { method = "GET", body, formData, auth = false, headers: extraHeaders = {} }: RequestOptions = {},
): Promise<T> {
  if (auth && accessToken && accessTokenExpiresAt - Date.now() < REFRESH_MARGIN_MS) {
    await refreshSession();
  }

  const perform = () => {
    const headers: Record<string, string> = { ...extraHeaders };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;
    return send(path, {
      method,
      headers,
      body: formData ?? (body !== undefined ? JSON.stringify(body) : undefined),
      credentials: path.startsWith("/api/auth/") ? "include" : "omit",
    });
  };

  let response = await perform();
  if (response.status === 401 && auth) {
    const renewed = await refreshSession();
    if (renewed) {
      response = await perform();
    }
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  // 202/204 o respuestas sin cuerpo (p. ej. "aprobar"): no hay JSON que leer
  const text = response.status === 204 ? "" : await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// ------------------------------------------------------------------ autenticación

export async function login(email: string, password: string): Promise<User> {
  const auth = await request<AuthResponse>("/api/auth/login", { method: "POST", body: { email, password } });
  setSession(auth);
  return auth.user;
}

export async function register(input: {
  email: string;
  password: string;
  displayName: string;
  acceptTerms: boolean;
}): Promise<User> {
  const auth = await request<AuthResponse>("/api/auth/register", { method: "POST", body: input });
  setSession(auth);
  return auth.user;
}

export async function logout(): Promise<void> {
  try {
    await request<void>("/api/auth/logout", { method: "POST" });
  } finally {
    setSession(null);
  }
}

// ------------------------------------------------------------------ datos

export function getHome(): Promise<HomeData> {
  return request<HomeData>("/api/public/home");
}

/** Devuelve null si el usuario todavía no ha creado su anuncio. */
export async function getMyAdProfile(): Promise<AdProfile | null> {
  try {
    return await request<AdProfile>("/api/me/ad-profile", { auth: true });
  } catch (error) {
    if (error instanceof ApiError && error.code === "AD_PROFILE_NOT_FOUND") {
      return null;
    }
    throw error;
  }
}

export function saveMyAdProfile(input: AdProfileInput): Promise<AdProfile> {
  return request<AdProfile>("/api/me/ad-profile", { method: "PUT", body: input, auth: true });
}

/** "Así se verá tu anuncio si ganas": la presentación montada con tu web. */
export function getMyShowcase(): Promise<MyShowcase> {
  return request<MyShowcase>("/api/me/ad-profile/showcase", { auth: true });
}

/** Vuelve a leer tu web (como mucho una vez cada 2 minutos). */
export function refreshMyShowcase(): Promise<MyShowcase> {
  return request<MyShowcase>("/api/me/ad-profile/showcase/refresh", { method: "POST", auth: true });
}

export function uploadImage(file: File): Promise<UploadedImage> {
  const formData = new FormData();
  formData.append("file", file);
  return request<UploadedImage>("/api/images", { method: "POST", formData, auth: true });
}

// ------------------------------------------------------------------ Arena

/**
 * Pujar. La Idempotency-Key identifica ESTE intento: si la conexión falla y se reintenta con la
 * misma clave, el servidor no la cuenta dos veces.
 */
export function placeBid(amountPoints: number, idempotencyKey: string): Promise<BidResponse> {
  return request<BidResponse>("/api/arena/bids", {
    method: "POST",
    body: { amountPoints },
    auth: true,
    headers: { "Idempotency-Key": idempotencyKey },
  });
}

export function getMyArenaStatus(): Promise<MyArenaStatus> {
  return request<MyArenaStatus>("/api/arena/me", { auth: true });
}

export function getMyBids(limit = 50): Promise<MyBid[]> {
  return request<MyBid[]>(`/api/arena/me/bids?limit=${limit}`, { auth: true });
}

export function getWallet(): Promise<WalletBalance> {
  return request<WalletBalance>("/api/me/wallet", { auth: true });
}

export function getHistory(page = 0, size = 10): Promise<HistoryPage> {
  return request<HistoryPage>(`/api/public/history?page=${page}&size=${size}`);
}

// ------------------------------------------------------------------ recuperar contraseña

/** Siempre responde igual, exista o no la cuenta (así nadie puede averiguar quién está registrado). */
export function requestPasswordReset(email: string): Promise<void> {
  return request<void>("/api/auth/password/forgot", { method: "POST", body: { email } });
}

export function resetPassword(token: string, newPassword: string): Promise<void> {
  return request<void>("/api/auth/password/reset", { method: "POST", body: { token, newPassword } });
}

// ------------------------------------------------------------------ Arena Points

export function getPoints(): Promise<PointsOverview> {
  return request<PointsOverview>("/api/me/points", { auth: true });
}

/** "Gana puntos": tus puntos y los proyectos que puedes ver hoy. */
export function getEarnOverview(): Promise<EarnOverview> {
  return request<EarnOverview>("/api/earn", { auth: true });
}

export function getPublicProject(id: string): Promise<PublicProject> {
  return request<PublicProject>(`/api/public/projects/${encodeURIComponent(id)}`);
}

/** Abrir la página de un proyecto: el servidor empieza a contar el tiempo. */
export function startProjectView(id: string): Promise<ViewStatus> {
  return request<ViewStatus>(`/api/earn/projects/${encodeURIComponent(id)}/start`, { method: "POST", auth: true });
}

/** 10 segundos más de visualización activa. */
/**
 * Tiempo mirando la web de un proyecto, en tramos de 10 s. Normalmente 1; varios si la web estuvo
 * abierta en otra ventana y acabas de volver (el servidor nunca da más de los que caben en el tiempo real).
 */
export function tickProjectView(id: string, ticks = 1): Promise<TickResult> {
  return request<TickResult>(`/api/earn/projects/${encodeURIComponent(id)}/tick`, {
    method: "POST",
    body: { ticks: Math.max(1, Math.min(6, Math.floor(ticks))) },
    auth: true,
  });
}

export function getTasks(): Promise<TasksOverview> {
  return request<TasksOverview>("/api/earn/tasks", { auth: true });
}

export function startTask(id: string): Promise<TaskStarted> {
  return request<TaskStarted>(`/api/earn/tasks/${encodeURIComponent(id)}/start`, { method: "POST", auth: true });
}

export function claimTask(id: string): Promise<TaskClaimed> {
  return request<TaskClaimed>(`/api/earn/tasks/${encodeURIComponent(id)}/claim`, { method: "POST", auth: true });
}

export function reportTask(id: string, reason: string): Promise<void> {
  return request<void>(`/api/earn/tasks/${encodeURIComponent(id)}/report`, { method: "POST", body: { reason }, auth: true });
}

// ------------------------------------------------------------------ Promocionar

export function getMyPromotions(): Promise<PromotionsOverview> {
  return request<PromotionsOverview>("/api/promotions", { auth: true });
}

export function createPromotion(input: { title: string; description: string; url: string }): Promise<Promotion> {
  return request<Promotion>("/api/promotions", { method: "POST", body: input, auth: true });
}

export function pausePromotion(id: string): Promise<Promotion> {
  return request<Promotion>(`/api/promotions/${encodeURIComponent(id)}/pause`, { method: "POST", auth: true });
}

export function resumePromotion(id: string): Promise<Promotion> {
  return request<Promotion>(`/api/promotions/${encodeURIComponent(id)}/resume`, { method: "POST", auth: true });
}

export function deletePromotion(id: string): Promise<void> {
  return request<void>(`/api/promotions/${encodeURIComponent(id)}`, { method: "DELETE", auth: true });
}

// ------------------------------------------------------------------ avisos

export function getNotifications(): Promise<NotificationsResponse> {
  return request<NotificationsResponse>("/api/me/notifications", { auth: true });
}

export function markAllNotificationsRead(): Promise<void> {
  return request<void>("/api/me/notifications/read-all", { method: "POST", auth: true });
}

// ------------------------------------------------------------------ administración

export function getAdminOverview(): Promise<AdminOverview> {
  return request<AdminOverview>("/api/admin/overview", { auth: true });
}

export function getPendingAdSlots(): Promise<AdminAdSlot[]> {
  return request<AdminAdSlot[]>("/api/admin/ad-slots/pending", { auth: true });
}

export function getRecentAdSlots(): Promise<AdminAdSlot[]> {
  return request<AdminAdSlot[]>("/api/admin/ad-slots/recent", { auth: true });
}

/** Vuelve a leer la web del ganador (su presentación) antes de aprobarlo. Va en segundo plano. */
export function refreshAdSlotShowcase(id: string): Promise<void> {
  return request<void>(`/api/admin/ad-slots/${encodeURIComponent(id)}/showcase/refresh`, { method: "POST", auth: true });
}

export function approveAdSlot(id: string): Promise<void> {
  return request<void>(`/api/admin/ad-slots/${encodeURIComponent(id)}/approve`, { method: "POST", auth: true });
}

export function rejectAdSlot(id: string, reason: string): Promise<{ refundedPoints: number; promotedSlotId: string | null }> {
  return request(`/api/admin/ad-slots/${encodeURIComponent(id)}/reject`, { method: "POST", body: { reason }, auth: true });
}

export function getAdminTasks(): Promise<AdminTask[]> {
  return request<AdminTask[]>("/api/admin/tasks", { auth: true });
}

export function hideTask(id: string, reason: string): Promise<void> {
  return request<void>(`/api/admin/tasks/${encodeURIComponent(id)}/hide`, { method: "POST", body: { reason }, auth: true });
}

export function restoreTask(id: string): Promise<void> {
  return request<void>(`/api/admin/tasks/${encodeURIComponent(id)}/restore`, { method: "POST", auth: true });
}

export function getSettings(): Promise<ArenaSettings> {
  return request<ArenaSettings>("/api/admin/settings", { auth: true });
}

export function updateSettings(settings: ArenaSettings): Promise<ArenaSettings> {
  return request<ArenaSettings>("/api/admin/settings", { method: "PUT", body: settings, auth: true });
}

export function getAuditLog(limit = 50): Promise<AuditEntry[]> {
  return request<AuditEntry[]>(`/api/admin/audit-log?limit=${limit}`, { auth: true });
}

