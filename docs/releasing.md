# Making a release: crib sheet

A release is a version tag (`v1.0.17`), with the installers on its release
page on GitHub. Linux is built by the tag itself; Windows only when asked,
as it costs twice the Actions minutes.

## 0. Before

- Everything committed and pushed: `git status` says *nothing to commit*.
- The tests pass: `./gradlew test`.
- Pick the next number: `git tag --sort=-v:refname | head -1` shows the
  last one (v1.0.16 → v1.0.17).

## 1. Tag it: Linux builds by itself

```
git tag v1.0.17
git push origin v1.0.17
```

GitHub's **Package** workflow runs the tests, builds
`myreceptionplanner_1.0.17_amd64.deb` and makes the **v1.0.17** release
page with it. About 4 minutes. Watch it on the repository's **Actions**
tab, or with `gh run watch`.

## 2. Windows, from the same tag

When the Linux run has finished (so the release page is there to add to):

On GitHub: **Actions** → **Package** (left) → **Run workflow** →
- *Use workflow from*: **Tags** → **v1.0.17** (not main, or the .msi
  does not reach the release page)
- **untick Linux** (already built), **tick Windows**
- **Run workflow**

Or from a terminal:

```
gh workflow run package.yml --ref v1.0.17 -f linux=false -f windows=true
```

About 6–8 minutes. It adds `MyReceptionPlanner-1.0.17.msi` to the page.

## 3. Check and describe

- The repository's **Releases**: v1.0.17 should have the `.deb` and the
  `.msi`.
- **Edit** the release to add a few lines on what is new
  (`git log v1.0.16..v1.0.17 --oneline` lists the changes).

## Installing it here

```
sudo apt install ./myreceptionplanner_1.0.17_amd64.deb
```

in a terminal window of your own (sudo needs one to ask for the password;
the `!` prompt in Claude Code cannot), or double-click the file.

## A trial build, no release

- Here, Linux only, free: `./gradlew packageApp -PappVersion=1.0.17`
  → `build/package/`.
- On GitHub, either system: **Run workflow** from **main**, tick what you
  want; download from the run's page (*Artifacts*), kept 2 days.

## If something goes wrong

- **A run failed**: open it on the Actions tab; the red step says why. Fix,
  commit, push, then move the tag to the fix and push it again:
  `git tag -f v1.0.17 && git push -f origin v1.0.17`.
- **"Artifact storage quota has been hit"**: the account's 500 MB of
  stored builds is full, from any of your private repositories. A release
  no longer stores one (its page has the files), but trial builds do. List
  and delete old ones on each repository's Actions page (or ask Claude);
  GitHub takes 6–12 hours to notice the space freed.
- **A wrong tag pushed**: `git push origin :refs/tags/v1.0.17` and
  `git tag -d v1.0.17` remove it; delete its release page on GitHub too.
