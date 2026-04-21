import { API_BASE_URL } from "../config/api";
import type { ReportResponse } from "../types/report";
import {
  DatasetValidationFailed,
  type DatasetValidationErrorResponse,
} from "../types/dataset";

function authHeader(): HeadersInit {
  const token = localStorage.getItem("accessToken");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function downloadDatasetTemplate(): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/datasets/template`, {
    headers: authHeader(),
  });
  if (!response.ok) {
    throw new Error("Failed to download template");
  }

  const blob = await response.blob();
  const filename = extractFilename(response.headers.get("Content-Disposition"))
    ?? "hawa-dataset-template.csv";

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export interface StartAnalysisFromCsvInput {
  file: File;
  dateFrom?: string;
  dateTo?: string;
}

export async function startAnalysisFromCsv(
  brandId: number,
  input: StartAnalysisFromCsvInput
): Promise<ReportResponse> {
  const form = new FormData();
  form.append("file", input.file);
  if (input.dateFrom) form.append("dateFrom", input.dateFrom);
  if (input.dateTo) form.append("dateTo", input.dateTo);

  const response = await fetch(
    `${API_BASE_URL}/api/brands/${brandId}/reports/csv`,
    { method: "POST", headers: authHeader(), body: form }
  );

  if (response.status === 201) {
    return response.json();
  }

  if (response.status === 400) {
    const body = (await response.json().catch(() => null)) as
      | DatasetValidationErrorResponse
      | { message?: string }
      | null;
    if (body && "errors" in body && Array.isArray(body.errors)) {
      throw new DatasetValidationFailed(body.errors);
    }
    throw new Error(body?.message || "Invalid request");
  }

  if (response.status === 413) {
    throw new Error("File too large (max 10 MB)");
  }

  const body = await response.json().catch(() => null);
  throw new Error(body?.message || "Failed to start analysis");
}

function extractFilename(header: string | null): string | null {
  if (!header) return null;
  const match = /filename="?([^"]+)"?/i.exec(header);
  return match?.[1] ?? null;
}
