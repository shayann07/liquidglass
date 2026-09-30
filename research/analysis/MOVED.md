# Where the loose files went (2026-09-14)

`analysis/` had 104 loose files. The 68 below were named by **no document and no script** in the
tree, so they were moved into folders by kind. Nothing was deleted, nothing was renamed, and every
file that any document or tool references stayed exactly where it was.

| File | Moved to | bytes |
| :-- | :-- | --: |
| `ASTRA-MESSAGE-round3.txt` | `analysis/correspondence/` | 6,597 |
| `ASTRA-MESSAGE-round6.txt` | `analysis/correspondence/` | 6,069 |
| `ASTRA-MESSAGE-round7.txt` | `analysis/correspondence/` | 5,640 |
| `ASTRA-MESSAGE-round8.txt` | `analysis/correspondence/` | 7,035 |
| `ASTRA-MESSAGE-round9.txt` | `analysis/correspondence/` | 6,529 |
| `C6c_maps.log` | `analysis/logs/` | 2,296 |
| `C6c_slow_r0_steps.log` | `analysis/logs/` | 267 |
| `C6c_slow_r100_traj.log` | `analysis/logs/` | 158 |
| `C6c_slow_r50_dark_traj.log` | `analysis/logs/` | 138 |
| `C6c_slow_r50_steps.log` | `analysis/logs/` | 269 |
| `_Tw_dark.npy` | `analysis/scratch/` | 17,774,768 |
| `_Tw_dark.png` | `analysis/scratch/` | 426,814 |
| `_Tw_dark_f120.npy` | `analysis/scratch/` | 35,549,408 |
| `_Tw_light.npy` | `analysis/scratch/` | 17,774,768 |
| `_Tw_light.png` | `analysis/scratch/` | 429,991 |
| `_f000120_crop.png` | `analysis/scratch/` | 264,524 |
| `_f004260_crop.png` | `analysis/scratch/` | 235,893 |
| `_look_others.png` | `analysis/scratch/` | 680,331 |
| `_look_pusm.png` | `analysis/scratch/` | 734,697 |
| `_v_LOLL8185_40.png` | `analysis/scratch/` | 672,765 |
| `_v_LOLL8185_8.png` | `analysis/scratch/` | 271,932 |
| `_v_LOLL8185_90.png` | `analysis/scratch/` | 491,770 |
| `_v_LQCO6923_40.png` | `analysis/scratch/` | 590,389 |
| `_v_LQCO6923_8.png` | `analysis/scratch/` | 506,715 |
| `_v_LQCO6923_90.png` | `analysis/scratch/` | 519,227 |
| `_wallpaper_check.png` | `analysis/scratch/` | 856,549 |
| `_widget_vs_ref.png` | `analysis/scratch/` | 83,712 |
| `build_jvmtest.log` | `analysis/logs/` | 3,988 |
| `build_restlens.log` | `analysis/logs/` | 7 |
| `build_toolbar.log` | `analysis/logs/` | 7 |
| `build_vitals2.log` | `analysis/logs/` | 7 |
| `build_vitals3.log` | `analysis/logs/` | 7 |
| `c9_lens_profile.log` | `analysis/logs/` | 279 |
| `c9_r100.log` | `analysis/logs/` | 154 |
| `c9_steps.log` | `analysis/logs/` | 382 |
| `emulator.log` | `analysis/logs/` | 10,750 |
| `emulator2.log` | `analysis/logs/` | 10,751 |
| `primaries_colour_map.json` | `analysis/scratch/` | 273 |
| `run.log` | `analysis/logs/` | 0 |
| `s24_calib.log` | `analysis/logs/` | 1,375 |
| `s24_calib2.log` | `analysis/logs/` | 355 |
| `s24_framestats_six-nowide.txt` | `analysis/device/s24/` | 46,957 |
| `s24_framestats_six-nowide_before.txt` | `analysis/device/s24/` | 46,847 |
| `s24_framestats_six.txt` | `analysis/device/s24/` | 46,951 |
| `s24_framestats_six_before.txt` | `analysis/device/s24/` | 46,839 |
| `s24_framestats_vitals.txt` | `analysis/device/s24/` | 46,908 |
| `s24_framestats_vitals_axis.txt` | `analysis/device/s24/` | 46,935 |
| `s24_framestats_vitals_before.txt` | `analysis/device/s24/` | 46,771 |
| `s24_framestats_vitals_final.txt` | `analysis/device/s24/` | 46,724 |
| `s24_framestats_vitals_fused.txt` | `analysis/device/s24/` | 46,797 |
| `scan2_a.log` | `analysis/logs/` | 43 |
| `scan2_b.log` | `analysis/logs/` | 157 |
| `scan_C6c_r0.log` | `analysis/logs/` | 36 |
| `scan_C6c_r100.log` | `analysis/logs/` | 37 |
| `scan_C6c_r50.log` | `analysis/logs/` | 36 |
| `scan_C6c_r50_dark.log` | `analysis/logs/` | 41 |
| `scan_C6c_slow_r0.log` | `analysis/logs/` | 42 |
| `scan_C6c_slow_r100.log` | `analysis/logs/` | 44 |
| `scan_C6c_slow_r50.log` | `analysis/logs/` | 43 |
| `scan_C6c_slow_r50_dark.log` | `analysis/logs/` | 47 |
| `scan_C6c_vel40.log` | `analysis/logs/` | 36 |
| `scan_ckpt.log` | `analysis/logs/` | 36 |
| `scan_pull.log` | `analysis/logs/` | 88 |
| `wallpaper_patch_colours_LOLL8185.json` | `analysis/scratch/` | 371 |
| `wallpaper_patch_colours_LQCO6923.json` | `analysis/scratch/` | 328 |
| `wallpaper_transform.npy` | `analysis/scratch/` | 152 |
| `wallpaper_transform_LQCO.npy` | `analysis/scratch/` | 160 |
| `wallpaper_transform_dark.npy` | `analysis/scratch/` | 152 |

## The folders

- `analysis/logs/` — build, scan and device session logs from the measurement and fitting passes.
- `analysis/device/s24/` — raw `dumpsys gfxinfo` framestats dumps from the Galaxy S24+ passes.
- `analysis/scratch/` — exploratory crops, contact sheets, `.npy` transforms and colour-patch dumps
  written while measuring. Kept because they are cheap to keep, not because anything cites them.
- `analysis/correspondence/` — the outgoing ASTRA round messages. The **rulings**
  (`ASTRA-RULING-round*.md`) stayed at the analysis root, because `PLAN-RESULTS.md` cites them there.

## What deliberately did not move

Every `.md`; every file a document or a tool names by path — including `model.json`,
`model_tables.md`, `material_fit*.json`, `toolbar_fit.*`, `manifest.json`, `manifest_check.md`,
`device_vs_ios.md`, `library_vs_ios.md`, `tint_sweep.md`, `_elements_zoom.png` (cited by
`FINDINGS.md`) and `build_sample.log` / `build_vitals.log` (written by `v3/tools/v3_build.sh`);
and every existing subdirectory.

## One file from outside analysis/

`outbox_server.log` sat loose at the capture root and is the mac outbox rig's session log; it moved
to `analysis/logs/` with the rest. Nothing else outside `analysis/` was touched: the capture root
keeps its three orientation documents and the two calibration targets, and `datasets/ios27-phone/`, `mac/`,
`library/`, `outbox/`, `targets/` and `tools/` are unchanged.
