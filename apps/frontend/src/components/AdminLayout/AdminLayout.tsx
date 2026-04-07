import { useState, useRef, useEffect } from "react";
import { Outlet, NavLink, useNavigate } from "react-router-dom";
import { Users, BarChart3, MessageSquareWarning, Building2, Tag, LogOut, Menu, X } from "lucide-react";
import { useAuth } from "../../context/useAuth";
import { logout } from "../../services/authService";
import hawaLogo from "../../assets/hawa-logo-cropped.png";
import "./AdminLayout.css";

const navItems = [
  { to: "/admin/users", label: "Users", icon: Users },
  { to: "/admin/companies", label: "Companies", icon: Building2 },
  { to: "/admin/brands", label: "Brands", icon: Tag },
  { to: "/admin/analytics", label: "Analytics", icon: BarChart3 },
  { to: "/admin/reviews", label: "Reported Reviews", icon: MessageSquareWarning },
];

const AdminLayout = (): React.JSX.Element => {
  const { user, refreshToken, clearAuth } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const topbarRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const el = topbarRef.current;
    if (!el) return;
    const update = () => {
      document.documentElement.style.setProperty("--topbar-height", `${el.offsetHeight}px`);
    };
    const observer = new ResizeObserver(update);
    observer.observe(el);
    update();
    return () => observer.disconnect();
  }, []);

  const handleLogout = async () => {
    try {
      if (refreshToken) await logout(refreshToken);
    } finally {
      clearAuth();
      navigate("/login", { replace: true });
    }
  };

  return (
    <div className="admin-layout">
      <header className="admin-topbar" ref={topbarRef}>
        <button
          className="admin-topbar-toggle"
          onClick={() => setSidebarOpen(!sidebarOpen)}
          aria-label="Toggle sidebar"
        >
          {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
        <img src={hawaLogo} alt="Hawa" className="admin-topbar-logo" />
        <div className="admin-topbar-user">
          <span className="admin-topbar-name">
            {user?.firstName} {user?.lastName}
          </span>
          <span className="admin-topbar-role">Admin</span>
        </div>
      </header>

      <aside className={`admin-sidebar ${sidebarOpen ? "admin-sidebar--open" : ""}`}>
        <nav className="admin-sidebar-nav">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `admin-nav-item ${isActive ? "admin-nav-item--active" : ""}`
              }
              onClick={() => setSidebarOpen(false)}
            >
              <Icon size={20} />
              <span className="admin-nav-label">{label}</span>
            </NavLink>
          ))}
        </nav>
        <button className="admin-nav-item admin-logout-btn" onClick={handleLogout}>
          <LogOut size={20} />
          <span className="admin-nav-label">Logout</span>
        </button>
      </aside>

      {sidebarOpen && (
        <div
          className="admin-sidebar-backdrop"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <main className="admin-content">
        <Outlet />
      </main>
    </div>
  );
};

export default AdminLayout;
