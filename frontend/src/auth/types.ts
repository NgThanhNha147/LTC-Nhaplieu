export interface AuthMenu {
  id?: string;
  code: string;
  label: string;
  path: string;
  icon?: string;
  parentCode?: string;
  displayOrder?: number;
  requiredFunction?: string;
  children?: AuthMenu[];
}

export interface AuthUser {
  id: string;
  username: string;
  displayName: string;
  email?: string;
  departmentId?: string;
  roles: string[];
  functions: string[];
  menus?: AuthMenu[];
  mustChangePassword?: boolean;
  status?: string;
  rowVersion?: number;
}

export interface LoginResponse {
  accessToken: string;
  tokenType?: string;
  expiresIn?: number;
  user?: AuthUser;
}

export interface ManagedUser {
  id: string;
  username: string;
  displayName: string;
  email?: string;
  status: string;
  mustChangePassword: boolean;
  rowVersion: number;
  roles: string[];
  functions?: string[];
  menus?: AuthMenu[];
  createdAt?: string;
  updatedAt?: string;
}

export interface ManagedRole {
  id: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
  systemRole: boolean;
  rowVersion: number;
  functions: string[];
}

export interface FunctionDefinition {
  id: string;
  code: string;
  name: string;
  functionGroup?: string;
  description?: string;
}

export interface AuditEvent {
  id: string;
  eventType: string;
  action: string;
  actorUsername?: string;
  objectType?: string;
  objectId?: string;
  result: string;
  ipAddress?: string;
  requestId?: string;
  createdAt: string;
}
