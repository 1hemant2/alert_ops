const TEAM_ROLE_LABELS: Record<string, string> = {
  TEAM_OWNER: 'Team owner',
  ADMIN: 'Admin',
  USER: 'User',
  NONE: 'No access',
}

const TEAM_ROLE_DESCRIPTIONS: Record<string, string> = {
  TEAM_OWNER: 'Can edit or delete this team, invite or remove members, and create, view, edit, and delete tasks.',
  ADMIN: 'Can invite or remove members and create, view, edit, and delete tasks.',
  USER: 'Can create, view, edit, and delete tasks in this team. Cannot invite or remove members.',
  NONE: 'Has no permissions in this team.',
}

export function teamRoleLabel(role: string) {
  return TEAM_ROLE_LABELS[role] ?? role.replaceAll('_', ' ')
}

export function teamRoleDescription(role: string) {
  return TEAM_ROLE_DESCRIPTIONS[role] ?? 'Permissions are assigned by the server.'
}
