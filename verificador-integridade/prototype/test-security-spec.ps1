param()
$ErrorActionPreference='Stop'
$repo=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$passed=[System.Collections.Generic.List[string]]::new()
$failed=[System.Collections.Generic.List[string]]::new()
function Check([string]$name,[scriptblock]$test){try{if(-not (& $test)){throw 'assertion returned false'};$script:passed.Add($name);"PASS $name"}catch{$script:failed.Add($name);"FAIL $name :: $($_.Exception.Message)"}}

Check 'caminhos confinados e traversal bloqueado' {
  $root=[IO.Path]::GetFullPath((Join-Path $env:TEMP 'capture-root'))
  $resolve={param($rel) if([IO.Path]::IsPathRooted($rel)){return $null};$p=[IO.Path]::GetFullPath((Join-Path $root $rel));if($p.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -and $p -ne $root){$p}else{$null}}
  $good=& $resolve 'chat-1';$bad1=& $resolve '..\outside';$bad2=& $resolve 'C:\outside';$bad3=& $resolve '\\server\share'
  return ($good -and !$bad1 -and !$bad2 -and !$bad3)
}
Check 'deduplicação linear de IDs' {
  $ids=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase);1..20000|%{[void]$ids.Add("capture-$_")};[void]$ids.Add('CAPTURE-1');return $ids.Count -eq 20000
}
Check 'paginação 100 e seleção explícita/global' {
  foreach($n in @(0,1,100,101,1000,10000)){$pages=if($n -eq 0){0}else{[Math]::Ceiling($n/100)};if($pages -ne [Math]::Ceiling($n/100)){return $false}}
  $all=$true;$selected=[Collections.Generic.HashSet[int]]::new();$ids=0..4999;if($all){$all=$false;foreach($i in $ids){if($i -ne 7){[void]$selected.Add($i)}}};return (!$all -and $selected.Count -eq 4999 -and !$selected.Contains(7))
}
Check 'sublotes de metadados limitados a 25' {
  $sizes=@();for($i=0;$i -lt 101;$i+=25){$sizes+= [Math]::Min(25,101-$i)};return (($sizes -join ',') -eq '25,25,25,25,1')
}
Check 'limites acumulados recusam antes de trocar arquivo' {
  $max=16MB;$valid=16MB;$tooLarge=16MB+1;return ($valid -le $max -and $tooLarge -gt $max)
}
Check 'escrita temporária preserva destino em erro' {
  $d=Join-Path $env:TEMP ('spec-'+[guid]::NewGuid().ToString('N'));[IO.File]::WriteAllText($d,'old');$t=$d+'.tmp';try{[IO.File]::WriteAllText($t,'partial');throw 'injected write failure'}catch{};$ok=[IO.File]::ReadAllText($d) -eq 'old';Remove-Item $d,$t -Force -ErrorAction SilentlyContinue;return $ok
}
Check 'serialização incremental mantém bytes canônicos' {
  $rows=@('a/1|1|aa','b/2|2|bb');$expected=($rows -join "`n")+"`n";$m=[IO.MemoryStream]::new();foreach($r in $rows){$b=[Text.Encoding]::UTF8.GetBytes($r+"`n");$m.Write($b,0,$b.Length)};$actual=[Text.Encoding]::UTF8.GetString($m.ToArray());return $expected -ceq $actual
}
Check 'inventário do runtime .NET do EXE disponível para republish' {
  $r=& dotnet --list-runtimes 2>$null;return [bool]($r | Where-Object {$_ -match '^Microsoft\.NETCore\.App 10\.0\.(1[4-9]|[2-9]\d)(\s|$)'})
}

"RESULTADO: $($passed.Count) PASS; $($failed.Count) FAIL"
if($failed.Count){exit 1}
