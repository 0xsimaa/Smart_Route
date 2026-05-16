# Security policy

## Supported versions

| Version | Supported |
|---------|-----------|
| 2.x     | Yes       |

## Reporting vulnerabilities

If you discover a security issue in Smart Route itself (not third-party apps you test with it), please:

1. **Do not** open a public issue for exploit details.  
2. Email the maintainer or open a private security advisory on GitHub.  
3. Include steps to reproduce and impact assessment.

## Threat model (honest)

**In scope for this project**

- Reliability of mock injection while backgrounded  
- Session recovery without exposing unintended coordinates  
- Secure storage of user-defined routes on device  

**Out of scope / known limits**

- Hiding mock status from apps that call `Location.isMockProvider()`  
- Guaranteeing zero leak of real GPS on all OEM ROMs without root  
- iOS production system-wide mock (not supported)  

## User responsibilities

- Obtain written authorization before testing third-party applications.  
- Store GPX/audit exports according to your organization’s data policy.  
- Remove mock app designation when testing is complete.  
