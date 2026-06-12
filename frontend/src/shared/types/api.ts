export type {
  ApiErrorResponse,
  ApiResponse,
  PageResponse,
} from "@/services/api_client";

export type EntityId = string | number;

export type ListQuery = {
  page?: number;
  size?: number;
  search?: string;
  sort?: string;
};
