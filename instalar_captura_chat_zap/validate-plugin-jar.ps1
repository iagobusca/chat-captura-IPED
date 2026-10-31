param([Parameter(Mandatory=$true)][string]$Jar)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[IO.Compression.ZipFile]::OpenRead($Jar)
try{
 $required=@('iped/viewers/HtmlLinkViewer.class','iped/viewers/CaptureIntegrity.class','iped/viewers/ChatCapturePanel.class','iped/viewers/ChatAnnotationStore.class','iped/viewers/AnnotationPreflight.class','iped/viewers/localization/Messages.class','iped-viewer-messages.properties','iped-viewer-messages_pt_BR.properties')
 foreach($n in $required){if(!$z.GetEntry($n)){throw "Entrada obrigatoria ausente: $n"}}
 foreach($n in @('iped-viewer-messages.properties','iped-viewer-messages_pt_BR.properties')){$r=[IO.StreamReader]::new($z.GetEntry($n).Open());try{$t=$r.ReadToEnd()}finally{$r.Dispose()};if($t -notmatch '(?m)^HexViewerPlus\.appName=' -or $t -notmatch '(?m)^ChatCapturePanel\.IncludeAnnotations='){throw "Bundle invalido: $n"}}
}finally{$z.Dispose()}