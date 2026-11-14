# Libraries included in the installers

The [MIT licence](LICENSE) covers MyReceptionPlanner's own code. The
installers (`.deb` and `.msi`) also include these, each under its own licence:

| Library | What for | Licence |
|---|---|---|
| iText 5 (`com.itextpdf:itextpdf`) | the PDFs | GNU Affero General Public License v3 (AGPL-3.0) |
| FlatLaf (`com.formdev:flatlaf`) | the look | Apache License 2.0 |
| MigLayout (`com.miglayout:miglayout-swing`) | the forms | BSD 3-Clause |
| dom4j (`org.dom4j:dom4j`) | the XML files | BSD-style (dom4j licence) |
| JSVG (`com.github.weisj:jsvg`) | the emblem | MIT |
| Eclipse Temurin | the Java it runs on | GPL-2.0 with the Classpath Exception |

Because iText 5 is under the AGPL, the installers as a whole are
distributed on the AGPL's terms: the full source of MyReceptionPlanner is in
this repository, and iText's at <https://github.com/itext/itextpdf>.
