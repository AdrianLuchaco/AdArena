/** Tipos de las respuestas del backend (mismos nombres que los DTO de Java). */

export type Role = "USER" | "ADMIN";

export interface User {
  id: string;
  email: string;
  displayName: string;
  role: Role;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresAt: string;
  expiresIn: number;
  user: User;
}

export interface AdProfile {
  companyName: string;
  websiteUrl: string;
  description: string;
  imageId: string;
  imageUrl: string;
  updatedAt: string;
}

export interface AdProfileInput {
  companyName: string;
  websiteUrl: string;
  description: string;
  imageId: string | null;
}

export interface UploadedImage {
  id: string;
  url: string;
  contentType: string;
  width: number;
  height: number;
  sizeBytes: number;
}

// ------------------------------------------------------------------ portada y Arena

export type HomeState = "AD" | "PENDING_REVIEW" | "NO_BIDS" | "NO_AD";

// ------------------------------------------------------------------ webs de los proyectos

/**
 * FRAME: la web se ve dentro de AdArena y los puntos cuentan mientras la miras.
 * WINDOW: la web no se deja mostrar dentro de otras; se abre en su propia ventana.
 */
export type ViewMode = "FRAME" | "WINDOW";

/** Una web en el visor y en las tarjetas (lo que AdArena ha leído de ella). */
export interface SiteInfo {
  mode: ViewMode;
  /** Lo que se carga en el visor (solo FRAME): la web o su reproductor oficial */
  frameUrl: string | null;
  /** La dirección para abrirla aparte */
  openUrl: string;
  domain: string;
  siteName: string;
  title: string | null;
  description: string | null;
  iconUrl: string | null;
  /** Su foto principal */
  imageUrl: string | null;
  /** "#1f7a5a" */
  themeColor: string | null;
}

/** La presentación animada del ganador, montada con su web. */
export interface Showcase {
  siteName: string;
  domain: string;
  title: string | null;
  description: string | null;
  themeColor: string | null;
  iconUrl: string | null;
  heroImageUrl: string | null;
  galleryUrls: string[];
  highlights: string[];
}

export type ShowcaseStatus = "NONE" | "PENDING" | "READY" | "FAILED";

export interface MyShowcase {
  status: ShowcaseStatus;
  showcase: Showcase | null;
  websiteUrl: string | null;
  fetchedAt: string | null;
  error: string | null;
  canRefreshAt: string | null;
}

export interface CurrentAd {
  companyName: string;
  description: string;
  websiteUrl: string;
  imageUrl: string;
  startsAt: string;
  endsAt: string;
  /** Lo que pujó en total para ganar */
  wonWithPoints: number;
  /** Día en que compitió y ganó (AAAA-MM-DD) */
  roundDate: string;
  /** Su presentación animada (aprobada); null si no se pudo leer su web */
  showcase: Showcase | null;
}

/** Un proyecto que compite hoy. */
export interface ProjectEntry {
  id: string;
  position: number;
  companyName: string;
  description: string;
  websiteUrl: string;
  imageUrl: string;
  totalPoints: number;
  carriedInPoints: number;
}

export interface RoundSummary {
  id: string;
  roundDate: string;
  endsAt: string;
  participants: number;
  ranking: ProjectEntry[];
}

export interface HomeData {
  serverTime: string;
  state: HomeState;
  currentAd: CurrentAd | null;
  round: RoundSummary | null;
}

export interface MyArenaStatus {
  roundOpen: boolean;
  endsAt: string | null;
  hasAdProfile: boolean;
  totalPoints: number;
  carriedInPoints: number;
  position: number | null;
  minNextBidPoints: number;
  availablePoints: number;
  reservedPoints: number;
}

export interface BidResponse {
  totalPoints: number;
  position: number;
  endsAt: string;
  extended: boolean;
  availablePoints: number;
  reservedPoints: number;
  replayed: boolean;
}

export interface WalletBalance {
  availablePoints: number;
  reservedPoints: number;
}

export type BidType = "BID" | "CARRY_OVER" | "CARRY_REVERSAL";

export interface MyBid {
  createdAt: string;
  auctionDate: string;
  type: BidType;
  amountPoints: number;
  totalAfterPoints: number;
}

export type NotificationType =
  | "OUTBID"
  | "AUCTION_WON"
  | "AUCTION_LOST"
  | "AD_APPROVED"
  | "AD_REJECTED"
  | "WINNER_REFUNDED"
  | "CANDIDATE_PROMOTED"
  | "TASK_HIDDEN";

/** Aviso privado que llega al instante por WebSocket. */
export interface ArenaNotification {
  type: NotificationType;
  title: string;
  message: string;
  link: string;
}

export interface NotificationItem {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  link: string | null;
  createdAt: string;
  read: boolean;
}

export interface NotificationsResponse {
  unreadCount: number;
  items: NotificationItem[];
}

// ------------------------------------------------------------------ Arena Points

export type MovementType =
  | "TOP_UP"
  | "BID_RESERVE"
  | "BID_WIN_CHARGE"
  | "BID_FORFEIT"
  | "WINNER_REFUND"
  | "ADMIN_ADJUSTMENT"
  | "SIGNUP_BONUS"
  | "VIEW_REWARD"
  | "TASK_REWARD"
  | "TEST_GRANT"
  | "WINNER_BONUS";

export interface Movement {
  createdAt: string;
  type: MovementType;
  description: string | null;
  amountPoints: number;
  balanceAfterPoints: number;
}

export interface PointsOverview {
  availablePoints: number;
  reservedPoints: number;
  movements: Movement[];
}

/** Reglas para ganar puntos (los números reales del servidor). */
export interface RewardRules {
  tickSeconds: number;
  tickPoints: number;
  bonusAfterSeconds: number;
  bonusPoints: number;
  dailyCapPerProject: number;
  taskRewardPoints: number;
  taskMinSeconds: number;
  tasksPerDay: number;
  signupBonus: number;
  /** Premio al ganador cuando su anuncio sale en portada */
  winnerBonus: number;
}

export interface EarnProject {
  id: string;
  position: number;
  companyName: string;
  description: string;
  imageUrl: string;
  totalPoints: number;
  own: boolean;
  pointsEarnedToday: number;
  dailyCap: number;
  site: SiteInfo;
}

export interface EarnOverview {
  availablePoints: number;
  reservedPoints: number;
  earnedTodayFromViews: number;
  earnedTodayFromTasks: number;
  tasksDoneToday: number;
  rules: RewardRules;
  projects: EarnProject[];
}

export type ViewBlockReason = "OWN_PROJECT" | "CAP_REACHED" | "NOT_IN_ARENA";

export interface ViewStatus {
  projectId: string;
  canEarn: boolean;
  reason: ViewBlockReason | null;
  pointsEarnedToday: number;
  dailyCap: number;
  ticks: number;
  bonusAwarded: boolean;
  rules: RewardRules;
}

export interface TickResult {
  pointsAwarded: number;
  /** Tramos de 10 s que se han contado */
  ticksAwarded: number;
  bonusAwarded: boolean;
  pointsEarnedToday: number;
  dailyCap: number;
  capReached: boolean;
  availablePoints: number;
}

export interface PublicProject {
  id: string;
  companyName: string;
  description: string;
  websiteUrl: string;
  imageUrl: string;
  totalPoints: number;
  carriedInPoints: number;
  position: number | null;
  inArena: boolean;
  roundEndsAt: string | null;
  site: SiteInfo;
}

export type SocialPlatform = "YOUTUBE" | "X" | "INSTAGRAM" | "TIKTOK" | "TWITCH" | "LINKEDIN" | "FACEBOOK" | "GITHUB" | "WEB";

export type TaskState = "AVAILABLE" | "STARTED" | "DONE";

export interface TaskItem {
  id: string;
  platform: SocialPlatform;
  platformLabel: string;
  title: string;
  description: string | null;
  url: string;
  rewardPoints: number;
  /** Destacado por el admin: sale el primero y da más puntos */
  featured: boolean;
  state: TaskState;
  startedAt: string | null;
  site: SiteInfo;
}

export interface TasksOverview {
  tasksDoneToday: number;
  tasksPerDay: number;
  earnedToday: number;
  rules: RewardRules;
  tasks: TaskItem[];
}

export interface TaskStarted {
  id: string;
  url: string;
  minSeconds: number;
  startedAt: string;
}

export interface TaskClaimed {
  id: string;
  pointsAwarded: number;
  tasksDoneToday: number;
  tasksPerDay: number;
  availablePoints: number;
}

export type PromotionStatus = "ACTIVE" | "PAUSED" | "HIDDEN";

export interface Promotion {
  id: string;
  platform: SocialPlatform;
  platformLabel: string;
  title: string;
  description: string | null;
  url: string;
  status: PromotionStatus;
  hiddenReason: string | null;
  rewardPoints: number;
  totalVisits: number;
  visitsToday: number;
  createdAt: string;
  site: SiteInfo;
}

export interface PromotionsOverview {
  maxActive: number;
  rewardPoints: number;
  promotions: Promotion[];
}

// ------------------------------------------------------------------ historial

export type PastOutcome = "WINNER" | "PENDING_REVIEW" | "NO_BIDS" | "NO_WINNER";

export interface PastProject {
  id: string;
  rank: number;
  companyName: string;
  description: string;
  websiteUrl: string;
  imageUrl: string;
  totalPoints: number;
}

export interface PastRound {
  roundDate: string;
  showcaseDate: string;
  closedAt: string;
  outcome: PastOutcome;
  winner: PastProject | null;
  projects: PastProject[];
}

export interface HistoryPage {
  items: PastRound[];
  page: number;
  totalPages: number;
  totalItems: number;
}

/** Formato de error de la API (RFC 9457 + code + errors). */
export interface ProblemBody {
  status: number;
  code?: string;
  detail?: string;
  errors?: Record<string, string>;
}

// ------------------------------------------------------------------ administración

export interface AdminOverview {
  spentPoints: number;
  issuedPoints: number;
  issuedTodayPoints: number;
  usersAvailablePoints: number;
  usersReservedPoints: number;
  ledgerMismatches: number;
  users: number;
  pendingAdSlots: number;
  activeTasks: number;
  reportedTasks: number;
  hiddenTasks: number;
  failedEmails: number;
  emailsDelivered: boolean;
  openRound: { id: string; roundDate: string; endsAt: string; participants: number; totalPoints: number } | null;
}

export type AdSlotStatus = "PENDING_REVIEW" | "APPROVED" | "REJECTED" | "EXPIRED";

export interface AdminAdSlot {
  id: string;
  status: AdSlotStatus;
  candidateRank: number;
  amountPoints: number;
  roundDate: string | null;
  startsAt: string;
  endsAt: string;
  userEmail: string | null;
  userName: string | null;
  ad: { companyName: string; description: string; websiteUrl: string; imageUrl: string } | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string;
  /** Su presentación: congelada si ya se aprobó; si está pendiente, la que se congelaría ahora */
  showcase: Showcase | null;
}

export interface AdminTask {
  id: string;
  platform: SocialPlatform;
  title: string;
  description: string | null;
  url: string;
  status: PromotionStatus;
  hiddenReason: string | null;
  reports: number;
  completions: number;
  featured: boolean;
  rewardPoints: number;
  ownerEmail: string | null;
  ownerName: string | null;
  createdAt: string;
  reportReasons: string[];
}

export interface ArenaSettings {
  minBidPoints: number;
  minIncrementPoints: number;
  carryOverPercent: number;
  closeTime: string;
  timeZone: string;
  antiSnipingWindowSeconds: number;
  antiSnipingExtensionSeconds: number;
  antiSnipingMaxExtensions: number;
  updatedAt?: string | null;
}

export interface AuditEntry {
  id: string;
  adminName: string | null;
  action: string;
  targetType: string;
  targetId: string;
  details: string | null;
  createdAt: string;
}
