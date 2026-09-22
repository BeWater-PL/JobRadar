# JobRadar – Recherche maschinenlesbare Stellenlisten (Stand 15.09.2026, per curl geprüft)

| Arbeitgeber | Funktioniert | URL | Format | Anzahl Stellen |
|---|---|---|---|---|
| Vorwerk | teilweise | https://jobs.vorwerkgroup.com/sitemap.xml (Liste: https://jobs.vorwerkgroup.com/search/?q=&locale=de_DE) | SAP SuccessFactors – nur Sitemap-XML (URLs) + HTML-Liste, kein JSON | 69 |
| Knipex | **ja** | https://karriere.knipex.de/jobs.feed.json | softgarden – JSON (schema.org DataFeed) | 27 |
| Barmenia Gothaer | **ja** | https://api.smartrecruiters.com/v1/companies/BarmeniaGothaerAG/postings?limit=100 | SmartRecruiters – JSON (paginiert via offset) | 385 |
| Coroplast | teilweise | https://jobs.coroplast.de/sitemap.xml (Liste: https://jobs.coroplast.de/search/?q=&locale=de_DE) | SAP SuccessFactors – nur Sitemap-XML + HTML, kein JSON | 24 |
| Riedel Communications | **ja** | https://www.riedel.net/jobs.json | softgarden-Proxy – JSON (totalNumberOfJobs, results[]) | 53 |
| GEDORE | nein | https://recruitingapp-5584.de.umantis.com/Jobs/All?lang=ger | Haufe umantis – nur HTML (kein RSS/XML gefunden) | 10 |
| WSW | nein | https://wsw-online.softgarden.io/de/vacancies | softgarden – nur HTML (kein jobs.json wie bei Riedel/Knipex) | 20 |
| Schmersal | **ja** | https://schmersal.onlyfy.io/job/list/8b38kyrbny9hfkkhxzedhvrhnm979rq?format=json&max_results=100 | onlyfy (Prescreen) – JSON (totalResults, jobs[]) | 19 |
| Vaillant | teilweise | https://jobs.vaillant-group.com/sitemap.xml | SAP SuccessFactors – Sitemap-XML; HTML-Liste per curl leer (JS-gerendert) | 234 (weltweit) |
| gkv informatik | nein | https://www.gkvi.de/karriere | eigene Website – nur HTML (Links /karriere/stellenangebote/…) | 7 |
| Erfurt & Sohn | **ja** | POST https://jobs.erfurt.com/talention/api/3.2/job (Body `{}`, Content-Type application/json) | Talention – JSON (results[], resultsTotal) | 7 |
| Akzenta | nein | https://rundum-akzenta.de/karriere/ | HR4YOU (akzenta.hr4you.org, Login-Wall) – nur HTML-Links auf hr4you.org/job/view/ID | 15 |
| bilstein group | **ja** | https://bilsteingroup.com/api/guidecom/api/v2/offersummaries (Header `Accept: application/json` nötig, sonst HTML-Debugseite) | Guidecom/Talentsoft-Proxy – JSON (ausschreibungen[]) | 21 |
| Interamt | **ja** | https://interamt.de/koop/app/webservice_v2?partner=<ID> (Stadt Wuppertal = 1714, Gebäudemanagement Wuppertal = 2732, Jobcenter Wuppertal = 1409) | JSON (Anzahltreffer, Stellenangebote[]); ohne partner → Fehler | 1 / 3 / 4 (gesamt Interamt: 12.245) |
| Stadt Wuppertal | nein (Interamt ja) | https://www.wuppertal.de/rathaus-buergerservice/ausbildung_stellen/stellenangebote/stellenangebote.php | eigene Website – HTML (Cloudflare, Browser-Header nötig); nur 1 Stelle auf Interamt | 45 |
| Wupperverband | **ja** | https://karriere.wupperverband.de/jobPublication/list.json | d.vinci – JSON (Array) | 8 |

## Negativ geprüft (alle 404/leer)
- Personio `*.jobs.personio.de/xml` für alle Firmen → 429/nicht vorhanden
- Recruitee `/api/offers`, join.com, Greenhouse boards-api, Lever – keine Treffer
- softgarden `/api/rest/v3/frontend/jobslist` → 401; `/jobs.json` nur wenn Kunde es auf eigener Domain proxied (Riedel, Knipex)
- Interamt `webservice_v2?id=…` liefert nur Details zu bekannten IDs

## Empfehlung Reihenfolge Implementierung
1. JSON direkt: Barmenia Gothaer, Riedel, Knipex, Schmersal, Wupperverband, bilstein group, Erfurt (POST), Interamt (partner 1714/2732/1409)
2. Sitemap-XML: Vorwerk, Coroplast, Vaillant (nur URL+Slug, Titel aus Slug ableiten)
3. HTML-Scraping: WSW (softgarden), GEDORE (umantis), gkvi, Stadt Wuppertal, Akzenta

## Geprueft 09/2026, nicht angebunden

Zweite Runde, wieder per curl. Neu angebunden wurden daraus nur Aptiv
(Workday) und codecentric (Personio-XML) — der Rest liefert nichts
Maschinenlesbares oder ist ausdruecklich gesperrt:

- **Vorwerk, Bayer Wuppertal, Vaillant Remscheid** — SAP SuccessFactors. Die
  `robots.txt` sperrt `/services/` und damit den RSS-Ausgang. Bewusst nicht
  abgefragt.
- **BARMER** — BeeSite (milch & zucker), nur HTML und Sitemap, kein Feed.
- **WSW** — softgarden ohne JSON-Feed auf eigener Domain, derzeit ausserdem
  keine IT-Stellen.
- **Bergische Universitaet** — QIS-Server, nur HTML.
- **Riedel** — von `riedel.net/jobs.json` (404 seit 09/2026) auf ein
  softgarden-Board ohne Feed umgezogen. Quelle steht auf `aktiv: false`.

Diese Arbeitgeber kommen weiter ueber die Arbeitsagentur und die
Job-Alert-Mails herein.
