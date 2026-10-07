"""Derive a once-only intro and compact runtime loop, preserving source downloads.

Usage: python tools/build_black_fox_music.py PATH_TO_FFMPEG
Requires numpy for waveform alignment; no network and no change to source downloads.
"""
import hashlib
import json
import subprocess
import sys
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'art/black_fox/music/epicbattle_j'
OUTPUT = ROOT / 'src/main/resources/assets/maid_weapon/sounds/black_fox'
ffmpeg = sys.argv[1]
full = SOURCE / 'PerituneMaterial_EpicBattle_J.mp3'
loop = SOURCE / 'PerituneMaterial_EpicBattle_J_loop.ogg'

def decode(path):
    data = subprocess.check_output([ffmpeg, '-v', 'error', '-i', str(path),
                                   '-ac', '1', '-ar', '4000', '-f', 'f32le', '-'])
    return np.frombuffer(data, dtype='<f4').astype(np.float64)

original, repeat = decode(full), decode(loop)
reference = repeat[4000:4000 * 9]  # Ignore boundary compression differences.
size = 1 << (len(original) + len(reference) - 1).bit_length()
correlation = np.fft.irfft(np.fft.rfft(original, size) * np.fft.rfft(reference[::-1], size), size)
correlation = correlation[len(reference)-1:len(original)]
squares = np.concatenate(([0.0], np.cumsum(original * original)))
energy = squares[len(reference):] - squares[:-len(reference)]
scores = correlation / np.sqrt(np.maximum(energy * np.dot(reference, reference), 1e-12))
# The official loop already contains the opening musical passage. Keep only the
# unique lead-in before its earliest confidently matching occurrence, not a
# redundant complete cycle. Local peaks avoid choosing a neighbouring sample.
peaks = np.flatnonzero((scores[1:-1] > scores[:-2]) &
                      (scores[1:-1] >= scores[2:]) & (scores[1:-1] > .99)) + 1
candidates = peaks[(peaks / 4000 - 1 > .05) & (peaks / 4000 - 1 < len(repeat) / 4000)]
assert len(candidates), 'No trustworthy early loop entry; do not trim arbitrarily.'
offset = int(candidates[0])
confidence = float(scores[offset])
intro_seconds = offset / 4000 - 1
assert confidence > .99 and intro_seconds > .05, (confidence, intro_seconds)
OUTPUT.mkdir(parents=True, exist_ok=True)
subprocess.run([ffmpeg, '-v', 'error', '-y', '-i', str(full), '-t', str(intro_seconds),
                '-ar', '48000', '-ac', '2', '-c:a', 'libvorbis', '-q:a', '3',
                str(OUTPUT / 'epicbattle_j_intro.ogg')], check=True)
subprocess.run([ffmpeg, '-v', 'error', '-y', '-i', str(loop),
                '-ar', '48000', '-ac', '2', '-c:a', 'libvorbis', '-q:a', '3',
                str(OUTPUT / 'epicbattle_j_loop.ogg')], check=True)
# Check the actual encoded lead-in/loop join against the corresponding source
# passage, including the boundary omitted from the long alignment reference.
# Vorbis decoders can expose final block padding beyond the OGG granule length.
# Runtime limits the lead-in to this exact frame count before joining the loop.
intro_frames = round(intro_seconds * 48000)
lead = decode(OUTPUT / 'epicbattle_j_intro.ogg')[:round(intro_seconds * 4000)]
runtime_loop = decode(OUTPUT / 'epicbattle_j_loop.ogg')
joined = np.concatenate((lead, runtime_loop[:8000]))
expected = original[:len(joined)]
join_score = float(np.dot(joined, expected) /
                   np.sqrt(np.dot(joined, joined) * np.dot(expected, expected)))
assert join_score > .95, ('Encoded lead-in join differs from source', join_score)
manifest = {
    'track': 'EpicBattle_J', 'author': 'PeriTune', 'license': 'CC-BY-4.0',
    'source_page': 'https://peritune.com/blog/2021/09/16/epicbattle_j/',
    'license_page': 'https://peritune.com/about/',
    'intro_seconds': intro_seconds, 'waveform_alignment_score': confidence,
    'intro_frames_48000': intro_frames,
    'encoded_entry_alignment_score': join_score,
    'changes': 'Only the unique lead-in before the earliest waveform-matched loop entry is retained from the official MP3; the author loop already contains the opening musical passage. Runtime files use 48 kHz stereo Vorbis quality 3; source downloads unchanged. Runtime adds a three-second gain fade-in.',
    'sha256': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
               for path in (full, loop, SOURCE / 'PerituneMaterial_EpicBattle_J_loop.zip',
                            OUTPUT / 'epicbattle_j_intro.ogg', OUTPUT / 'epicbattle_j_loop.ogg')}
}
(SOURCE / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
(ROOT / 'src/main/resources/licenses/black_fox_music/MANIFEST.json').write_text(
    json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print('BLACK_FOX_MUSIC_BUILD_PASS', json.dumps(manifest, ensure_ascii=False))
