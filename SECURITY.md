# Security policy

## Reporting a vulnerability

Use [GitHub's private reporting form](https://github.com/shayann07/liquidglass/security/advisories/new).
Include the affected revision, platform, minimal reproduction and impact. Do not put credentials,
private recordings or exploit details in a public issue. There is no guaranteed response SLA.

The current development branch and latest published version are reviewed for reported issues.
A snapshot is experimental; fixes are made on `main`, with release decisions documented in the
changelog. Historical research branches are retained for provenance and receive no maintenance.

## Trust boundaries

Glass samples only content the host application records and explicitly supplies. It must not
capture other applications, bypass protected media, or include private screenshots in reports.
A visual blur is not redaction: remove sensitive content before recording it. A magnifier can
make content readable, and a transparent element is never an access-control boundary.

CI runs untrusted pull-request code with a read-only token and no publishing credentials.
Publishing credentials are used only in the tag workflow; maintainers must review and test a
release before creating a matching version tag. Dependency updates remain subject to review.

## Repository controls

The public repository uses GitHub's built-in Dependabot alerts and security updates, private
vulnerability reporting, secret scanning and push protection. Weekly grouped dependency PRs
limit notification noise. Standard GitHub-hosted runners are used; no paid bot subscription is
required. Main requires a passing build, an approving review and resolved conversations; force
pushes and branch deletion are prohibited. Administrator bypass remains possible and must be
an explicit maintainer decision. See [contributing](CONTRIBUTING.md) for validation.
