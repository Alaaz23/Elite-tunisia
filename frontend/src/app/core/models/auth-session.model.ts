export interface AuthSession {
  accessToken: string;
  expiresIn: number;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string;
  roles: string[];
  status: 'ACTIVE' | 'DISABLED';
}