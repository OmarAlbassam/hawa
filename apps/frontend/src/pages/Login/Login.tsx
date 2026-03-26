import { useState } from "react";
import { useNavigate } from "react-router-dom";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import { login } from "../../services/authService";
import type { LoginRequest } from "../../types/auth";
import "./Login.css";

const Login = (): React.JSX.Element => {
  const navigate = useNavigate();
  const [form, setForm] = useState<LoginRequest>({ email: "", password: "" });
  const [errors, setErrors] = useState<Partial<Record<keyof LoginRequest, string>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const validate = (): boolean => {
    const newErrors: Partial<Record<keyof LoginRequest, string>> = {};
    if (!form.email.trim()) {
      newErrors.email = "Email is required";
    }
    if (!form.password) {
      newErrors.password = "Password is required";
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const authResponse = await login(form);
      localStorage.setItem("accessToken", authResponse.accessToken);
      localStorage.setItem("refreshToken", authResponse.refreshToken);
      localStorage.setItem("user", JSON.stringify(authResponse.user));
      navigate("/");
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setSubmitting(false);
    }
  };

  const handleChange = (field: keyof LoginRequest, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  };

  return (
    <div className="Login">
      <div className="Login-card">
        <h1 className="Login-title">Hawa</h1>
        <p className="Login-subtitle">Sign in to your account</p>

        {submitError && <ErrorBanner message={submitError} />}

        <form className="Login-form" onSubmit={handleSubmit}>
          <div className="Login-field">
            <label className="Login-label" htmlFor="email">
              Email
            </label>
            <input
              id="email"
              className={`Login-input ${errors.email ? "Login-input--error" : ""}`}
              type="email"
              value={form.email}
              onChange={(e) => handleChange("email", e.target.value)}
              placeholder="you@example.com"
              autoComplete="email"
            />
            {errors.email && <span className="Login-error">{errors.email}</span>}
          </div>

          <div className="Login-field">
            <label className="Login-label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              className={`Login-input ${errors.password ? "Login-input--error" : ""}`}
              type="password"
              value={form.password}
              onChange={(e) => handleChange("password", e.target.value)}
              placeholder="Enter your password"
              autoComplete="current-password"
            />
            {errors.password && <span className="Login-error">{errors.password}</span>}
          </div>

          <button className="Login-submit" type="submit" disabled={submitting}>
            {submitting ? "Signing in..." : "Sign in"}
          </button>
        </form>
      </div>
    </div>
  );
};

export default Login;
