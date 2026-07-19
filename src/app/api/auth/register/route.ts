import { hash } from "bcryptjs";
import { NextRequest } from "next/server";
import { successResponse, errorResponse } from "@/lib/api-utils";
import { db } from "@/lib/db";

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { name, email, password, clinicName, specialty, licenseNumber } = body;

    // Validate required fields
    if (!name || !email || !password) {
      return errorResponse("Name, email, and password are required", 400);
    }

    if (typeof email !== "string" || !email.includes("@")) {
      return errorResponse("Invalid email address", 400);
    }

    if (typeof password !== "string" || password.length < 8) {
      return errorResponse("Password must be at least 8 characters", 400);
    }

    // Check if email already exists
    const existingUser = await db.user.findUnique({
      where: { email: email.toLowerCase().trim() },
    });

    if (existingUser) {
      return errorResponse("An account with this email already exists", 409);
    }

    // Auto-generate slug from name
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
    while (await db.user.findUnique({ where: { slug } })) {
      slug = `${baseSlug}.${slugSuffix}`;
      slugSuffix++;
    }

    // Hash password
    const passwordHash = await hash(password, 12);

    // Create user
    const user = await db.user.create({
      data: {
        name,
        email: email.toLowerCase().trim(),
        passwordHash,
        role: "provider",
        slug,
        clinicName: clinicName || null,
        specialty: specialty || null,
        licenseNumber: licenseNumber || null,
      },
    });

    // Create initial room for the user
    const roomName = slug;
    const room = await db.room.create({
      data: {
        title: `${name}'s Room`,
        providerId: user.id,
        roomName,
        isActive: false,
        e2eeEnabled: true,
      },
    });

    // Audit log
    await db.auditLog.create({
      data: {
        userId: user.id,
        action: "register",
        resource: "user",
        details: `Provider registered: ${name} (${email}), room created: ${roomName}`,
        ipAddress:
          request.headers.get("x-forwarded-for") ||
          request.headers.get("x-real-ip") ||
          null,
        userAgent: request.headers.get("user-agent") || null,
      },
    });

    // Return user without password
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { passwordHash: _unused, ...userWithoutPassword } = user;

    return successResponse(
      { user: userWithoutPassword, room },
      201
    );
  } catch (error) {
    console.error("Registration error:", error);
    return errorResponse("Failed to create account", 500);
  }
}
