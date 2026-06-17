import { apiClient, type ApiResponse } from "@/services/api_client";

export type ProductService = {
  productId: string;
  name: string;
  category: "ROOM" | "EVENT_SPACE" | "SERVICE";
  description?: string;
  unitPrice: number;
  unit?: string;
  status: "ACTIVE" | "INACTIVE";
};

const ENDPOINT = "/product-services";

export const productService = {
  async getList(category?: "ROOM" | "EVENT_SPACE" | "SERVICE") {
    const response = await apiClient.get<ApiResponse<ProductService[]>>(
      ENDPOINT,
      { params: category ? { category } : {} }
    );
    return response.data;
  }
};
