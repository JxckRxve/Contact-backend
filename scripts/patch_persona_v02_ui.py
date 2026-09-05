from pathlib import Path

p=Path('app/src/main/java/com/jrstudio/svyazsbogom/ui/GosRootApp.kt')
s=p.read_text()
marker='''                        Text("XP ${persona.xp} • GEN ${persona.genome.generation} • ${persona.genome.planning.uppercase()}", color = GosMuted.copy(alpha = .75f), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))'''
insert='''                        Text("XP ${persona.xp} • GEN ${persona.genome.generation} • ${persona.genome.planning.uppercase()}", color = GosMuted.copy(alpha = .75f), fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                        GosPersonaActions(
                            identityId = identityId,
                            identitySecret = identitySecret,
                            persona = persona,
                            onReload = onReload
                        )'''
if 'GosPersonaActions(' not in s:
    if marker not in s: raise SystemExit('Persona card marker missing')
    s=s.replace(marker,insert,1)
p.write_text(s)
