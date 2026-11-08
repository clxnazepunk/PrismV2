"""Show lint findings in customized areas without suppressing any diagnostics."""
from pathlib import Path
import xml.etree.ElementTree as ET

report = Path("app/build/reports/lint-results-debug.xml")
names = {
    "ArtistIdentities.kt", "ArtistImageSources.kt", "ArtistIdentityStore.kt",
    "ArtistIdentityUi.kt", "ArtistScreen.kt", "OfflineLibrary.kt", "LibraryScreen.kt",
    "LocalPlaylistSuggestions.kt", "PlaylistSuggestions.kt", "PlaylistCovers.kt",
    "Backup.kt", "AppContainer.kt", "LicensesPage.kt", "build.gradle.kts",
}
if not report.exists():
    print("Lint did not produce an XML report; see the lint step failure.")
else:
    issues = ET.parse(report).getroot().findall("issue")
    count = 0
    for issue in issues:
        locations = [loc for loc in issue.findall("location") if Path(loc.get("file", "")).name in names]
        if not locations:
            continue
        count += 1
        for loc in locations:
            print(f"{issue.get('severity')} [{issue.get('id')}] {loc.get('file')}:{loc.get('line', '?')}: {issue.get('message')}")
    print(f"Lint findings touching customized files: {count}. No filters or baselines were changed.")

