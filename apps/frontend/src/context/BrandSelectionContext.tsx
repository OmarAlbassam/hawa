import { useCallback, useEffect, useMemo, useState } from "react";
import { getBrands } from "../services/brandService";
import type { BrandSummaryResponse } from "../types/brand";
import { BrandSelectionContext } from "./brandSelectionContextDef";
import type { BrandSelectionContextValue } from "./brandSelectionContextDef";

const STORAGE_KEY = "selectedBrandId";

const readStoredBrandId = (): number | null => {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
};

export function BrandSelectionProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [brands, setBrands] = useState<BrandSummaryResponse[]>([]);
  const [selectedBrandId, setSelectedBrandIdState] = useState<number | null>(
    readStoredBrandId
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await getBrands(0, 100);
      setBrands(page.content);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load brands"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  // Reconcile selection with the fetched list: if the stored id isn't in the list,
  // fall back to the first brand.
  useEffect(() => {
    if (loading || brands.length === 0) return;
    const exists =
      selectedBrandId != null &&
      brands.some((b) => b.brandId === selectedBrandId);
    if (!exists) {
      const fallback = brands[0].brandId;
      setSelectedBrandIdState(fallback);
      localStorage.setItem(STORAGE_KEY, String(fallback));
    }
  }, [loading, brands, selectedBrandId]);

  const setSelectedBrandId = useCallback((brandId: number) => {
    setSelectedBrandIdState(brandId);
    localStorage.setItem(STORAGE_KEY, String(brandId));
  }, []);

  const selectedBrand = useMemo(
    () => brands.find((b) => b.brandId === selectedBrandId) ?? null,
    [brands, selectedBrandId]
  );

  const value: BrandSelectionContextValue = {
    brands,
    selectedBrand,
    selectedBrandId,
    setSelectedBrandId,
    loading,
    error,
    refresh,
  };

  return (
    <BrandSelectionContext.Provider value={value}>
      {children}
    </BrandSelectionContext.Provider>
  );
}
