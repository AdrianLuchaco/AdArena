"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import * as api from "./api";
import type { User } from "./types";

type AuthStatus = "loading" | "authenticated" | "anonymous";

interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  login: (email: string, password: string) => Promise<User>;
  register: (input: { email: string; password: string; displayName: string; acceptTerms: boolean }) => Promise<User>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Guarda quién ha iniciado sesión y lo comparte con toda la web.
 * Al cargar la página intenta recuperar la sesión con la cookie (por eso no hay que volver a
 * entrar cada vez que recargas).
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");

  useEffect(() => {
    const unsubscribe = api.onSessionChange((next) => {
      setUser(next);
      setStatus(next ? "authenticated" : "anonymous");
    });
    api.refreshSession().catch(() => {
      // Backend caído: seguimos como visitante; las páginas muestran su propio error
      setStatus("anonymous");
    });
    return unsubscribe;
  }, []);

  const login = useCallback((email: string, password: string) => api.login(email, password), []);
  const register = useCallback(
    (input: { email: string; password: string; displayName: string; acceptTerms: boolean }) => api.register(input),
    [],
  );
  const logout = useCallback(() => api.logout(), []);

  const value = useMemo(() => ({ status, user, login, register, logout }), [status, user, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return context;
}
