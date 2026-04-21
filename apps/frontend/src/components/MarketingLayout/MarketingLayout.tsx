import { useState, useRef, useEffect } from "react";
import { Outlet, NavLink, useNavigate } from "react-router-dom";
import { LayoutDashboard, Tag, FileBarChart, LogOut, Menu, X } from "lucide-react";
import { useAuth } from "../../context/useAuth";
import { logout } from "../../services/authService";
import hawaLogo from "../../assets/hawa-logo-cropped.png";
import "./MarketingLayout.css";

const navItems = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/brands", label: "Brands", icon: Tag },
  { to: "/reports", label: "Reports", icon: FileBarChart },
];

const MarketingLayout = (): React.JSX.Element => {
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
    <div className="marketing-layout">
      <header className="marketing-topbar" ref={topbarRef}>
        <button
          className="marketing-topbar-toggle"
          onClick={() => setSidebarOpen(!sidebarOpen)}
          aria-label="Toggle sidebar"
        >
          {sidebarOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
        <img src={hawaLogo} alt="Hawa" width="768" height="450" className="marketing-topbar-logo" />
        <div className="marketing-topbar-user">
          <span className="marketing-topbar-name">
            {user?.firstName} {user?.lastName}
          </span>
          <span className="marketing-topbar-role">Marketing</span>
        </div>
      </header>

      <aside className={`marketing-sidebar ${sidebarOpen ? "marketing-sidebar--open" : ""}`}>
        <nav className="marketing-sidebar-nav">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `marketing-nav-item ${isActive ? "marketing-nav-item--active" : ""}`
              }
              onClick={() => setSidebarOpen(false)}
            >
              <Icon size={20} />
              <span className="marketing-nav-label">{label}</span>
            </NavLink>
          ))}
        </nav>
        <button className="marketing-nav-item marketing-logout-btn" onClick={handleLogout}>
          <LogOut size={20} />
          <span className="marketing-nav-label">Logout</span>
        </button>
      </aside>

      {sidebarOpen && (
        <div
          className="marketing-sidebar-backdrop"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <main className="marketing-content">
        <Outlet />
      </main>
    </div>
  );
};

export default MarketingLayout;
