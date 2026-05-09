import { createContext } from "react";
import type { BrandSummaryResponse } from "../types/brand";

export interface BrandSelectionContextValue {
  brands: BrandSummaryResponse[];
  selectedBrand: BrandSummaryResponse | null;
  selectedBrandId: number | null;
  setSelectedBrandId: (brandId: number) => void;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
}

export const BrandSelectionContext =
  createContext<BrandSelectionContextValue | null>(null);
