import { useEffect, useRef, useState } from "react";
import { Check, ChevronDown, Tag } from "lucide-react";
import { useBrandSelection } from "../../context/useBrandSelection";
import "./BrandSelector.css";

const BrandSelector = (): React.JSX.Element | null => {
  const { brands, selectedBrand, setSelectedBrandId, loading } =
    useBrandSelection();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onClick = (e: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  if (loading && brands.length === 0) {
    return (
      <div className="BrandSelector BrandSelector--static">
        <Tag size={16} aria-hidden />
        <span className="BrandSelector-label">Loading brands…</span>
      </div>
    );
  }

  if (brands.length === 0) return null;

  if (brands.length === 1) {
    return (
      <div className="BrandSelector BrandSelector--static">
        <Tag size={16} aria-hidden />
        <span className="BrandSelector-label">{brands[0].brandName}</span>
      </div>
    );
  }

  const handleSelect = (brandId: number) => {
    setSelectedBrandId(brandId);
    setOpen(false);
  };

  return (
    <div className="BrandSelector" ref={containerRef}>
      <button
        type="button"
        className="BrandSelector-trigger"
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label="Select brand"
      >
        <Tag size={16} aria-hidden />
        <span className="BrandSelector-label">
          {selectedBrand?.brandName ?? "Select a brand"}
        </span>
        <ChevronDown
          size={16}
          aria-hidden
          className={`BrandSelector-chevron ${
            open ? "BrandSelector-chevron--open" : ""
          }`}
        />
      </button>

      {open && (
        <ul className="BrandSelector-menu" role="listbox">
          {brands.map((brand) => {
            const active = brand.brandId === selectedBrand?.brandId;
            return (
              <li key={brand.brandId} role="none">
                <button
                  type="button"
                  role="option"
                  aria-selected={active}
                  className={`BrandSelector-option ${
                    active ? "BrandSelector-option--active" : ""
                  }`}
                  onClick={() => handleSelect(brand.brandId)}
                >
                  <span className="BrandSelector-option-name">
                    {brand.brandName}
                  </span>
                  {brand.industry && (
                    <span className="BrandSelector-option-industry">
                      {brand.industry}
                    </span>
                  )}
                  {active && <Check size={14} aria-hidden />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
};

export default BrandSelector;
