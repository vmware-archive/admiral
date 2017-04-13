param(
    [string]$in,
    [string]$outputs,
    [string]$closure_semaphore,
    [string]$closure_uri,
    [string]$token,
    [string]$source_name,
    [string]$trusted_certs
)

Add-Type -Language CSharp @"
public class Context {
    public string inputs;
    public string outputs;
    public string closure_semaphore;
    public string closure_uri;
}
"@;

$context = new-object Context
$context.inputs = $in;
$context.outputs = $outputs;
$context.closure_semaphore = $closure_semaphore;
$context.closure_uri = $closure_uri;

$global:target_link = $null

$certs = Get-Content $trusted_certs

function Initialize ($token) {
    function Execute-Delegate ($link, $operation, $body, $handler='None') {
        $headers = @{'Content-type' = 'application/json'; 'Accept' = 'application/json';
                     'x-xenon-auth-token' = $token}

    	$target_link = $link
    	$op = $operation.ToUpper()
    	$target_uri = build_target_uri ($context.closure_uri, $target_link)

    	if ($op -eq 'GET') {
	    $resp = Invoke-RestMethod -Uri $target_uri -Certificate $certs | ConvertTo-Json
    	} elseif ($op -eq 'POST') {
	    $resp = Invoke-RestMethod -Uri $target_uri -Method Post -Body $body -Headers $headers -Certificate $certs | ConvertTo-Json
    	} elseif ($op -eq 'PATCH') {
	    $resp = Invoke-RestMethod -Uri $target_uri -Method Patch -Body $body -Headers $headers -Certificate $certs | ConvertTo-Json
    	} elseif ($op -eq 'PUT') {
	    $resp = Invoke-RestMethod -Uri $target_uri -Method Put -Body $body -Headers $headers -Certificate $certs | ConvertTo-Json
    	} elseif ($op -eq 'DELETE') {
	    $resp = Invoke-RestMethod -Uri $target_uri -Method Delete -Certificate $certs | ConvertTo-Json
    	} else {
	    Write-Host 'Unsupported operation on Execute-Delegate!'
	    throw 'Unsupported operation on Execute-Delegate!'
    	}
    	if ($handler -ne 'None') {
	    $resp
    	}
    }
}

function build_target_uri ($closure_uri, $target_link) {
    $pattern = "/resources/closures/"
    $uri_head = $closure_uri.Split($pattern,1)[0]
    $target_uri = "$($uri_head)$($link)"
    $target_uri = [string]$target_uri
    return $target_uri
}

$inputs = @"
    $($context.inputs)
"@ | ConvertFrom-JSON;
 
$ScriptToRun = 'user_scripts/' + $source_name
&$ScriptToRun
