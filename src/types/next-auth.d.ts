import { DefaultSession, DefaultUser } from "next-auth";

declare module "next-auth" {
  interface Session {
    user: {
      id: string;
      role: string;
      slug: string | null;
      locale: string;
    } & DefaultSession["user"];
  }

  interface User extends DefaultUser {
    role: string;
    slug: string | null;
    locale: string;
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    id: string;
    role: string;
    slug: string | null;
    locale: string;
  }
}
