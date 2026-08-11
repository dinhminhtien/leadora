import { TaskDetailWorkspaceScreen } from "@/features/follow_up_task/screens/TaskDetailWorkspaceScreen";

/**
 * Full task workspace (§10.14). The list's drawer is the peek surface; this is
 * where the work happens, so it gets its own route and can be linked, bookmarked
 * and opened in a new tab.
 */
export default async function TaskDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <TaskDetailWorkspaceScreen taskId={id} />;
}
