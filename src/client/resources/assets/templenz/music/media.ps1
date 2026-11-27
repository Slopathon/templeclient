param([string]$Dir, [int]$ParentPid = 0)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Say($obj) {
    try {
        [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress))
        [Console]::Out.Flush()
    } catch {
        exit 0
    }
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
} catch {
    Say @{ error = 'unsupported' }
    exit 1
}

$asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [Type[]]@([Windows.Storage.Streams.IInputStream]))

function Await($op, [Type]$type) {
    $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    if (-not $task.Wait(4000)) { return $null }
    return $task.Result
}

$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$manager = Await ($managerType::RequestAsync()) $managerType
if ($null -eq $manager) {
    Say @{ error = 'unavailable' }
    exit 1
}

$commandFile = Join-Path $Dir 'command.txt'
$lastKey = ''
$artCount = 0
$artAgainAt = [DateTime]::MaxValue
$tick = 0

function Pick {
    $spotify = $null
    foreach ($s in $manager.GetSessions()) {
        if ($s.SourceAppUserModelId -like '*Spotify*') { $spotify = $s; break }
    }
    if ($null -ne $spotify) { return $spotify }
    return $manager.GetCurrentSession()
}

function SaveArt($props) {
    $ref = $props.Thumbnail
    if ($null -eq $ref) { return '' }
    $stream = Await ($ref.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    if ($null -eq $stream) { return '' }
    $script:artCount++
    $path = Join-Path $Dir ('art-' + $script:artCount + '.img')
    $net = $asStream.Invoke($null, @($stream))
    $memory = New-Object System.IO.MemoryStream
    try { $net.CopyTo($memory) } finally { $net.Close() }
    if ($memory.Length -eq 0) { return '' }
    [System.IO.File]::WriteAllBytes($path, $memory.ToArray())
    Get-ChildItem -Path $Dir -Filter 'art-*.img' | Where-Object { $_.FullName -ne $path } |
        Sort-Object LastWriteTime | Select-Object -SkipLast 1 | Remove-Item -ErrorAction SilentlyContinue
    return $path
}

while ($true) {
    $tick++
    if ($ParentPid -gt 0 -and ($tick % 10) -eq 0) {
        if ($null -eq (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit 0 }
    }
    try {
        $session = Pick
        if (Test-Path $commandFile) {
            $command = (Get-Content $commandFile -Raw).Trim()
            Remove-Item $commandFile -ErrorAction SilentlyContinue
            if ($null -ne $session) {
                switch ($command) {
                    'playpause' { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
                    'next' { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
                    'prev' { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
                }
            }
        }
        if ($null -eq $session) {
            Say @{ none = $true }
        } else {
            $props = Await ($session.TryGetMediaPropertiesAsync()) $propsType
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $title = ''; $artist = ''; $album = ''
            if ($null -ne $props) { $title = $props.Title; $artist = $props.Artist; $album = $props.AlbumTitle }
            $key = $title + '|' + $artist + '|' + $album
            $art = $null
            if ($key -ne $lastKey -and $title -ne '') {
                $lastKey = $key
                $art = SaveArt $props
                $artAgainAt = (Get-Date).AddSeconds(2)
            } elseif ((Get-Date) -gt $artAgainAt) {
                $artAgainAt = [DateTime]::MaxValue
                $art = SaveArt $props
            }
            $out = @{
                app = $session.SourceAppUserModelId
                spotify = ($session.SourceAppUserModelId -like '*Spotify*')
                title = $title
                artist = $artist
                album = $album
                status = $playback.PlaybackStatus.ToString()
                position = $timeline.Position.TotalSeconds
                duration = $timeline.EndTime.TotalSeconds
                updated = $timeline.LastUpdatedTime.ToUnixTimeMilliseconds()
                now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            }
            if ($art) { $out.art = $art }
            Say $out
        }
    } catch {
        Say @{ error = $_.Exception.Message }
    }
    Start-Sleep -Milliseconds 300
}
