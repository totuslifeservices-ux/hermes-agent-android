import { NextRequest } from "next/server";
import { successResponse, errorResponse, requireAuth } from "@/lib/api-utils";
import { db } from "@/lib/db";

export async function GET(request: NextRequest) {
  try {
    const { error, user } = await requireAuth();
    if (error) return error;

    const { searchParams } = new URL(request.url);
    const limit = Math.min(parseInt(searchParams.get("limit") || "50", 10), 100);
    const offset = Math.max(parseInt(searchParams.get("offset") || "0", 10), 0);

    const [sessions, total] = await Promise.all([
      db.sessionHistory.findMany({
        where: { providerId: user.id as string },
        orderBy: { date: "desc" },
        take: limit,
        skip: offset,
      }),
      db.sessionHistory.count({
        where: { providerId: user.id as string },
      }),
    ]);

    return successResponse({
      sessions,
      total,
      limit,
      offset,
    });
  } catch (error) {
    console.error("Session history error:", error);
    return errorResponse("Failed to fetch session history", 500);
  }
}
