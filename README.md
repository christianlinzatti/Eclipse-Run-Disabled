# Eclipse-Run-Disabled

Eclipse-Plugin, das JUnit-5/6-Tests **trotz `@Disabled`** ausführt – auch `@QuarkusTest`-Klassen mit
Quarkus 3.22+ (getestet gegen den Quellcode von Quarkus **3.27.2 LTS** und dem aktuellen `main`).

Kontextmenü (Package Explorer, Editor, Outline):

```
Run As   > JUnit Test (inkl. @Disabled)
Debug As > JUnit Test (inkl. @Disabled)
```

Der normale Eintrag „JUnit Test“ bleibt unverändert: `@Disabled` wird dort weiterhin respektiert.

## Warum reicht `-Djunit.jupiter.conditions.deactivate=...` nicht mehr?

1. Für JUnit selbst funktioniert die Property weiterhin – das korrekte Muster ist aber
   `org.junit.*DisabledCondition` (ein Muster wie `org.junit*Disabled` passt nicht, weil es den ganzen
   Klassennamen `org.junit.jupiter.engine.extension.DisabledCondition` treffen muss).
2. Seit dem Umbau des Test-Classloadings (Quarkus 3.22) entscheidet Quarkus' `FacadeClassLoader`
   **vor** JUnit, ob eine Klasse ein `@QuarkusTest` ist. Ist die Klasse mit `@Disabled` annotiert, wird
   sie absichtlich **nicht** in den `QuarkusClassLoader` geladen („do not bother creating a quarkus app
   for it“). Führt JUnit sie wegen der Property trotzdem aus, scheitert `QuarkusTestExtension` mit:

   ```
   ... should have been loaded with a QuarkusClassLoader, but instead it was loaded with AppClassLoader.
   This is caused by the FacadeClassLoader not correct identified this class as a QuarkusTest
   ```

## Lösung

Das Plugin startet den Test-JVM mit einem kleinen Java-Agent (`-javaagent:…`, keine Abhängigkeiten,
keine Bytecode-Änderungen), der

1. `junit.jupiter.conditions.deactivate=org.junit.*DisabledCondition` setzt (ein bereits gesetzter Wert
   wird ergänzt, nicht überschrieben), und
2. im aktuellen Quarkus-`FacadeClassLoader` die geprüfte Annotation `@Disabled` durch eine nie
   verwendete Annotation ersetzt. Dadurch werden `@Disabled`-Testklassen wieder korrekt als
   `@QuarkusTest` erkannt und im `QuarkusClassLoader` geladen.

Ohne Quarkus im Classpath macht Punkt 2 einfach nichts. In der Konsole erscheint:

```
[Eclipse-Run-Disabled] aktiv - @Disabled wird ignoriert (junit.jupiter.conditions.deactivate=org.junit.*DisabledCondition)
[Eclipse-Run-Disabled] Quarkus FacadeClassLoader angepasst: @Disabled-Testklassen werden mit dem QuarkusClassLoader geladen
```

## Installation

Voraussetzung: Eclipse 2024-06 oder neuer mit Java-Entwicklungstools (JDT); die Tests laufen mit Java 17+.

**Variante A – Update-Site (empfohlen):**
`Help > Install New Software… > Add… > Archive…` und `eclipse-run-disabled-updatesite.zip` auswählen,
Kategorie „Run @Disabled JUnit Tests“ anhaken, installieren, Eclipse neu starten.

**Variante B – dropins:** `io.github.christianlinzatti.rundisabled_<version>.jar` in den Ordner
`<eclipse>/dropins/` kopieren und Eclipse neu starten.

Beide Dateien gibt es unter *Releases* bzw. als Artefakt des GitHub-Actions-Builds.

## Verwendung

- Rechtsklick auf Projekt, Package, Testklasse oder Testmethode → **Run As > JUnit Test (inkl. @Disabled)**.
- Es entsteht eine eigene Run Configuration vom Typ **„JUnit (inkl. @Disabled)“** (Name endet auf
  „(inkl. Disabled)“). Sie hat dieselben Reiter wie eine normale JUnit-Konfiguration; „Rerun“ im
  JUnit-View verwendet sie automatisch weiter.
- **JUnit-Ansicht:** Rechtsklick auf einen Test (z. B. einen übersprungenen `@Disabled`-Test) →
  **Run (inkl. @Disabled)** bzw. **Debug (inkl. @Disabled)** (unten im Menü). Wie beim eingebauten
  „Run/Debug“ wird die Run Configuration des Testlaufs übernommen (VM-Argumente, Umgebung,
  Classpath) und nur auf den gewählten Test bzw. die gewählte Klasse eingeschränkt.
- Für beliebige andere Launch-Konfigurationen gibt es die Variable `${run_disabled_agent}`, z. B. als
  VM-Argument: `-javaagent:${run_disabled_agent}`

## Hinweise

- Nur `@Disabled` wird ignoriert. `@DisabledOnOs`, `@DisabledIf…`, `@EnabledIf…` usw. bleiben aktiv.
- Für eine `@Disabled`-Klasse startet Quarkus jetzt (wie für jeden anderen `@QuarkusTest`) die
  Anwendung inkl. Dev Services.
- Der Agent greift per Reflection auf interne Quarkus-Felder zu (`CustomLauncherInterceptor.facadeLoader`,
  `FacadeClassLoader.disabledAnnotation`). Ändert Quarkus diese, erscheint eine Meldung in der Konsole
  und nur Punkt 1 wirkt.

## Bauen

```bash
tests/agent-selftest/run.sh   # Selbsttest des Agents gegen einen nachgebauten FacadeClassLoader (nur JDK nötig)
mvn verify                    # Tycho-Build: Plugin, Feature, Update-Site (Java 21, Maven 3.9+)
```

Ergebnisse: `site/target/repository/plugins/*.jar` (dropins) und `site/target/*.zip` (Update-Site).
Ein Tag `v*` erzeugt über GitHub Actions automatisch ein Release mit beiden Dateien.

Zum Entwickeln: Ordner `plugin/` in Eclipse (mit PDE) importieren und als Eclipse Application starten.
