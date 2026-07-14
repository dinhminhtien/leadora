import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Tabbed shell for the authenticated area. Hosts a [StatefulNavigationShell]
/// so each tab keeps its own navigation stack and scroll position across tab
/// switches; [AnimatedBranchContainer] cross-fades between branches instead of
/// swapping content instantly.
class DashboardShell extends StatelessWidget {
  const DashboardShell({super.key, required this.navigationShell});

  final StatefulNavigationShell navigationShell;

  void _onTap(int index) {
    // `initialLocation: true` only when re-tapping the active tab → pop to root.
    navigationShell.goBranch(
      index,
      initialLocation: index == navigationShell.currentIndex,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: _onTap,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            selectedIcon: Icon(Icons.dashboard_rounded),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.handshake_outlined),
            selectedIcon: Icon(Icons.handshake_rounded),
            label: 'Deals',
          ),
          NavigationDestination(
            icon: Icon(Icons.people_alt_outlined),
            selectedIcon: Icon(Icons.people_alt_rounded),
            label: 'Leads',
          ),
          NavigationDestination(
            icon: Icon(Icons.checklist_outlined),
            selectedIcon: Icon(Icons.checklist_rounded),
            label: 'Tasks',
          ),
          NavigationDestination(
            icon: Icon(Icons.more_horiz_rounded),
            selectedIcon: Icon(Icons.more_horiz_rounded),
            label: 'More',
          ),
        ],
      ),
    );
  }
}

/// Branch container that fades between tabs (used as the shell route's
/// `navigatorContainerBuilder`). Inactive branches stay mounted — preserving
/// scroll position and state exactly like an [IndexedStack] — but are
/// non-interactive, ticker-frozen and fully transparent, so switching tabs is
/// a smooth 220 ms cross-fade with no flicker or rebuild.
class AnimatedBranchContainer extends StatelessWidget {
  const AnimatedBranchContainer({
    super.key,
    required this.currentIndex,
    required this.children,
  });

  final int currentIndex;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        for (var i = 0; i < children.length; i++)
          // AnimatedOpacity must stay OUTSIDE TickerMode: the deactivated
          // branch's ticker is muted, and a fade-out driven from inside it
          // would freeze mid-flight, leaving the old branch opaque on top.
          AnimatedOpacity(
            opacity: i == currentIndex ? 1 : 0,
            duration: const Duration(milliseconds: 220),
            curve: Curves.easeOut,
            child: IgnorePointer(
              ignoring: i != currentIndex,
              child: TickerMode(enabled: i == currentIndex, child: children[i]),
            ),
          ),
      ],
    );
  }
}
