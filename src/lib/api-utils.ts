import { NextResponse } from "next/server";
import { auth } from "@/lib/auth";

export type ApiResponse<T = any> = {
  success: boolean;
  data?: T;
  error?: string;
};

export function successResponse<T>(data: T, status = 200) {
  return NextResponse.json(
    { success: true, data },
    { status }
  );
}

export function errorResponse(error: string, status = 400) {
  return NextResponse.json(
    { success: false, error },
    { status }
  );
}

export async function requireAuth() {
  const session = await auth();
  if (!session?.user?.id) {
    return { error: errorResponse("Unauthorized", 401), user: null };
  }
  return { error: null, user: session.user };
}
