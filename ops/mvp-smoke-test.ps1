param(
    [string]$BackendBaseUrl = "http://localhost:8086/api",
    [string]$FrontendBaseUrl = "http://localhost:3005",
    [switch]$SkipBuildChecks,
    [switch]$SkipManualReminders
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "== $Message =="
}

function Assert-HttpOk {
    param(
        [string]$Name,
        [string]$Url
    )
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 20
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "$Name failed with HTTP $($response.StatusCode): $Url"
    }
    Write-Host "PASS $Name -> HTTP $($response.StatusCode)"
}

Write-Step "Runtime health"
Assert-HttpOk "Backend health" "$BackendBaseUrl/health"
Assert-HttpOk "Frontend shell" "$FrontendBaseUrl/"

Write-Step "Anonymous public read contract"
$publicRoutes = @(
    "$BackendBaseUrl/feeds/trending",
    "$BackendBaseUrl/search?q=shipspace"
)

foreach ($route in $publicRoutes) {
    Assert-HttpOk "Public route $route" $route
}

if (-not $SkipBuildChecks) {
    Write-Step "Frontend verification"
    Push-Location "$PSScriptRoot/../web"
    npm.cmd run lint
    npm.cmd run build
    Pop-Location

    Write-Step "Backend focused verification"
    Push-Location "$PSScriptRoot/.."
    $env:JAVA_HOME = $env:JAVA_HOME
    .\mvnw.cmd -q '-Dtest=AuthServiceRbacTest,BannedUserWriteFilterTest,MongoIndexInitializerTest,MultibaseServiceTest,GitHubOAuthServiceTest,JwtUtilTest,UserInterestProfileRepositoryTest' test
    .\mvnw.cmd -q -DskipTests compile
    Pop-Location
}

if (-not $SkipManualReminders) {
    Write-Step "Manual MVP gates still required"
    Write-Host "1. Complete real GitHub OAuth login in browser."
    Write-Host "2. Confirm normal GitHub user receives USER only."
    Write-Host "3. Confirm ADMIN_GITHUB_IDS user receives ADMIN and can access /admin/**."
    Write-Host "4. Ban a user and verify reads/logout work while create/like/comment/follow/upload are blocked."
    Write-Host "5. Upload valid PNG, JPEG, GIF, and WebP files."
    Write-Host "6. Verify oversized, invalid MIME, and renamed executable uploads are rejected cleanly."
    Write-Host "7. Confirm Multibase delivery policy does not expose private data unintentionally."
}

Write-Host ""
Write-Host "Smoke script completed."
