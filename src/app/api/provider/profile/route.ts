import { NextRequest } from "next/server";
import { successResponse, errorResponse, requireAuth } from "@/lib/api-utils";
import { db } from "@/lib/db";

export async function GET() {
  try {
    const { error, user } = await requireAuth();
    if (error) return error;

    const provider = await db.user.findUnique({
      where: { id: user.id as string },
      select: {
        id: true,
        name: true,
        email: true,
        image: true,
        role: true,
        slug: true,
        clinicName: true,
        clinicLogo: true,
        specialty: true,
        licenseNumber: true,
        locale: true,
        isActive: true,
        lastLoginAt: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    if (!provider) {
      return errorResponse("Provider not found", 404);
    }

    return successResponse({ provider });
  } catch (error) {
    console.error("Profile fetch error:", error);
    return errorResponse("Failed to fetch profile", 500);
  }
}

export async function PUT(request: NextRequest) {
  try {
    const { error, user } = await requireAuth();
    if (error) return error;

    const body = await request.json();
    const { name, clinicName, clinicLogo, specialty, licenseNumber, locale } =
      body;

    // Validate locale if provided
    if (locale && !["en", "fr"].includes(locale)) {
      return errorResponse("Locale must be 'en' or 'fr'", 400);
    }

    // Build update data (only include provided fields)
    const updateData: Record<string, unknown> = {};
    if (name !== undefined) updateData.name = name;
    if (clinicName !== undefined) updateData.clinicName = clinicName;
    if (clinicLogo !== undefined) updateData.clinicLogo = clinicLogo;
    if (specialty !== undefined) updateData.specialty = specialty;
    if (licenseNumber !== undefined) updateData.licenseNumber = licenseNumber;
    if (locale !== undefined) updateData.locale = locale;

    // If name changed, regenerate slug if needed
    if (name !== undefined && name !== user.name) {
      const baseSlug = name
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9\s-]/g, "")
        .replace(/\s+/g, ".")
        .replace(/-+/g, ".")
        .replace(/\.+/g, ".")
        .replace(/^\.|\.$/g, "");

      let slug = baseSlug;
      let slugSuffix = 1;
      while (
        await db.user.findFirst({
          where: { slug, id: { not: user.id as string } },
        })
      ) {
        slug = `${baseSlug}.${slugSuffix}`;
        slugSuffix++;
      }

      updateData.slug = slug;
    }

    const updatedProvider = await db.user.update({
      where: { id: user.id as string },
      data: updateData,
      select: {
        id: true,
        name: true,
        email: true,
        image: true,
        role: true,
        slug: true,
        clinicName: true,
        clinicLogo: true,
        specialty: true,
        licenseNumber: true,
        locale: true,
        isActive: true,
        lastLoginAt: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    // Audit log
    await db.auditLog.create({
      data: {
        userId: user.id as string,
        action: "profile.update",
        resource: "user",
        details: `Profile updated: ${Object.keys(updateData).join(", ")}`,
        ipAddress:
          request.headers.get("x-forwarded-for") ||
          request.headers.get("x-real-ip") ||
          null,
        userAgent: request.headers.get("user-agent") || null,
      },
    });

    return successResponse({ provider: updatedProvider });
  } catch (error) {
    console.error("Profile update error:", error);
    return errorResponse("Failed to update profile", 500);
  }
}
