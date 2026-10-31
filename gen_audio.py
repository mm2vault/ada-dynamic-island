import numpy as np, wave, os, subprocess

SR = 44100
DUR = 36.0
t = np.arange(int(SR * DUR)) / SR

def env(n, attack, release):
    e = np.ones(n)
    a = int(SR * attack); r = int(SR * release)
    if a > 0: e[:a] = np.linspace(0, 1, a) ** 1.5
    if r > 0: e[-r:] = np.linspace(1, 0, r) ** 1.5
    return e

def pad(freqs, amp=0.18, detune=0.6):
    out = np.zeros_like(t)
    for f in freqs:
        for d in (-detune, 0.0, detune):
            ph = np.random.rand() * 2 * np.pi
            out += np.sin(2 * np.pi * (f + d) * t + ph) * 0.33
            out += np.sin(2 * np.pi * (f * 2 + d) * t + ph) * 0.12
    lfo = 0.75 + 0.25 * np.sin(2 * np.pi * 0.05 * t)
    return out * amp * lfo

def lowpass(x, cut):
    # simple one-pole
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y

def reverbish(x, decay=0.35, delay=0.09):
    d = int(SR * delay)
    y = x.copy()
    for k in range(1, 6):
        shift = d * k
        y[shift:] += x[:-shift] * (decay ** k)
    return y

def soft_clip(x):
    return np.tanh(x * 1.2) * 0.9

def write(name, sig):
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * 0.85
    # loop crossfade at edges
    n = len(sig); f = int(SR * 1.2)
    fade = np.linspace(0, 1, f)
    sig[:f] *= fade
    sig[-f:] *= fade[::-1]
    data = (sig * 32767).astype(np.int16)
    os.makedirs("assets", exist_ok=True)
    wav = "/tmp/%s.wav" % name
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(data.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
                    "-codec:a", "libmp3lame", "-b:a", "80k", "assets/%s.mp3" % name], check=True)
    print("assets/%s.mp3" % name, os.path.getsize("assets/%s.mp3" % name) // 1024, "KB")

np.random.seed(7)

# 1) Gece Yolculugu - warm minor pad, slow motion
sig = pad([110.0, 164.81, 220.0, 261.63], amp=0.20)
sig += lowpass(np.random.randn(len(t)) * 0.05, 500) * 0.5
write("gece", soft_clip(reverbish(sig, 0.4, 0.11)))

# 2) Derin Odak - brighter, minimal, gentle pulse
pulse = (np.sin(2 * np.pi * 0.5 * t) > 0.85).astype(float)
sig = pad([146.83, 220.0, 293.66], amp=0.16)
sig += pad([440.0], amp=0.04)
sig += lowpass(np.random.randn(len(t)) * 0.02, 900) * pulse * 0.6
write("odak", soft_clip(reverbish(sig, 0.3, 0.08)))

# 3) Yagmurlu Pencere - airy drone + rain noise
rain = np.random.randn(len(t)) * 0.12
rain = lowpass(rain, 2600) - lowpass(rain, 700) * 0.8
drops = np.zeros_like(t)
for _ in range(260):
    p = np.random.randint(0, len(t) - SR // 4)
    ln = np.random.randint(300, 1400)
    drops[p:p + ln] += np.hanning(ln) * np.random.rand() * 0.25
sig = pad([87.31, 130.81, 196.0], amp=0.17) + rain * 0.55 + lowpass(drops, 5000) * 0.5
write("yagmur", soft_clip(reverbish(sig, 0.45, 0.13)))
