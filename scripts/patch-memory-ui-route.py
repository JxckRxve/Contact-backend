from pathlib import Path
p=Path('app/src/main/java/com/jrstudio/svyazsbogom/ui/GosRootApp.kt')
s=p.read_text()

s=s.replace('private enum class GosScreen { HOME, DIAGNOSTICS, SPACES, PERSONAS, TASK }','private enum class GosScreen { HOME, DIAGNOSTICS, MEMORY, SPACES, PERSONAS, TASK }',1)

old='''            GosScreen.DIAGNOSTICS -> GosScreen.HOME\n            GosScreen.HOME -> GosScreen.HOME'''
new='''            GosScreen.DIAGNOSTICS -> GosScreen.HOME\n            GosScreen.MEMORY -> GosScreen.HOME\n            GosScreen.HOME -> GosScreen.HOME'''
if old not in s: raise SystemExit('back handler marker missing')
s=s.replace(old,new,1)

old='''                onSpaces = { screen = GosScreen.SPACES },\n                onDiagnostics = { screen = GosScreen.DIAGNOSTICS },\n                onLegacy = onOpenLegacy,'''
new='''                onSpaces = { screen = GosScreen.SPACES },\n                onDiagnostics = { screen = GosScreen.DIAGNOSTICS },\n                onMemory = { screen = GosScreen.MEMORY },\n                onLegacy = onOpenLegacy,'''
if old not in s: raise SystemExit('home call marker missing')
s=s.replace(old,new,1)

old='''            GosScreen.DIAGNOSTICS -> GosDiagnostics(\n                identityId = identity.conversationId,\n                identitySecret = identity.installSecret,\n                onBack = { screen = GosScreen.HOME },\n                onCoreChanged = { scope.launch { loadCore() } }\n            )\n            GosScreen.SPACES -> GosSpaces('''
new='''            GosScreen.DIAGNOSTICS -> GosDiagnostics(\n                identityId = identity.conversationId,\n                identitySecret = identity.installSecret,\n                onBack = { screen = GosScreen.HOME },\n                onCoreChanged = { scope.launch { loadCore() } }\n            )\n            GosScreen.MEMORY -> GosMemoryScreen(\n                identityId = identity.conversationId,\n                identitySecret = identity.installSecret,\n                spaces = spaces,\n                onBack = { screen = GosScreen.HOME },\n                onChanged = { scope.launch { loadCore() } }\n            )\n            GosScreen.SPACES -> GosSpaces('''
if old not in s: raise SystemExit('screen route marker missing')
s=s.replace(old,new,1)

old='''    onSpaces: () -> Unit,\n    onDiagnostics: () -> Unit,\n    onLegacy: () -> Unit,'''
new='''    onSpaces: () -> Unit,\n    onDiagnostics: () -> Unit,\n    onMemory: () -> Unit,\n    onLegacy: () -> Unit,'''
if old not in s: raise SystemExit('home signature marker missing')
s=s.replace(old,new,1)

old='''        GosNavCard(\n            icon = Icons.Rounded.Hub,\n            title = "SPACES / PERSONAS",'''
new='''        GosNavCard(\n            icon = Icons.Rounded.Memory,\n            title = "MEMORY CORE",\n            subtitle = "Working / Episodic / Personal / Agent / Space + retrieval preview",\n            badge = "CORE v0.1",\n            onClick = onMemory\n        )\n        GosNavCard(\n            icon = Icons.Rounded.Hub,\n            title = "SPACES / PERSONAS",'''
if old not in s: raise SystemExit('home nav marker missing')
s=s.replace(old,new,1)

p.write_text(s)
