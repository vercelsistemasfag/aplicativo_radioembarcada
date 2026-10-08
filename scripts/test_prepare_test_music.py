import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import prepare_test_music as music


class MusicArchiveTest(unittest.TestCase):
    def make_zip(self, path, entries):
        with zipfile.ZipFile(path, "w") as archive:
            for name, contents in entries.items():
                archive.writestr(name, contents)

    def test_absent_url_is_optional_and_preserves_existing_local_tracks(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory)
            (destination / "local.mp3").write_bytes(b"existing")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(0, music.prepare_music("", destination))
            self.assertIn("biblioteca opcional não incluída", output.getvalue())
            self.assertEqual(b"existing", (destination / "local.mp3").read_bytes())

    def test_zip_copies_only_mp3_and_preserves_subfolders_and_existing_files(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "music"
            destination.mkdir()
            (destination / "existing.mp3").write_bytes(b"original")

            def download(url, archive):
                self.make_zip(archive, {"MPB/song.mp3": b"fixture", "rock80/disc/track.MP3": b"fixture",
                                        "existing.mp3": b"replacement", "readme.txt": b"ignored"})

            with patch.object(music, "download_archive", side_effect=download):
                self.assertEqual(3, music.prepare_music("https://example.invalid/archive", destination))
            self.assertTrue((destination / "MPB/song.mp3").exists())
            self.assertTrue((destination / "rock80/disc/track.MP3").exists())
            self.assertFalse((destination / "readme.txt").exists())
            self.assertEqual(b"original", (destination / "existing.mp3").read_bytes())

    def test_empty_archive_fails_clearly(self):
        with tempfile.TemporaryDirectory() as directory:
            def download(url, archive):
                self.make_zip(archive, {"readme.txt": "no tracks"})
            with patch.object(music, "download_archive", side_effect=download):
                with self.assertRaisesRegex(music.MusicArchiveError, "não contém MP3"):
                    music.prepare_music("https://example.invalid/archive", Path(directory))

    def test_zip_rejects_traversal_and_symlinks(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "archive"
            self.make_zip(archive, {"../outside.mp3": b"fixture"})
            with self.assertRaises(music.MusicArchiveError):
                music.extract_archive(archive, Path(directory))
            entry = zipfile.ZipInfo("link.mp3")
            entry.external_attr = 0o120777 << 16
            with zipfile.ZipFile(archive, "w") as data:
                data.writestr(entry, "target")
            with self.assertRaises(music.MusicArchiveError):
                music.extract_archive(archive, Path(directory))

    def test_rar4_and_rar5_are_detected_without_relying_on_url_extension(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "library.archive"
            for signature in (b"Rar!\x1a\x07\x00", b"Rar!\x1a\x07\x01\x00"):
                archive.write_bytes(signature)
                with patch.object(music.subprocess, "run", side_effect=[
                    subprocess.CompletedProcess([], 0, "MPB/song.mp3\n"),
                    subprocess.CompletedProcess([], 0),
                ]) as run:
                    music.extract_archive(archive, Path(directory))
                self.assertEqual("unrar", run.call_args_list[0].args[0][0])
                self.assertIn("-p-", run.call_args.args[0])

    def test_url_never_appears_in_download_arguments_or_failure_log(self):
        url = "https://example.invalid/archive?private=fixture"
        with patch.object(music.subprocess, "run", return_value=subprocess.CompletedProcess([], 22)) as run:
            with self.assertRaises(music.MusicArchiveError) as error:
                music.download_archive(url, Path("archive"))
            self.assertNotIn(url, str(run.call_args.args[0]))
            self.assertNotIn(url, str(error.exception))
            self.assertIn(url, run.call_args.kwargs["input"])

    def test_secret_takes_precedence_and_repository_variable_is_read_without_logging_url(self):
        with patch.dict(os.environ, {"MUSIC_ARCHIVE_URL": "secret-url",
                                    "MUSIC_ARCHIVE_VARIABLE_PRESENT": "true"}), \
                patch.object(music.subprocess, "run") as run:
            self.assertEqual("secret-url", music.archive_url())
            run.assert_not_called()
        with patch.dict(os.environ, {"MUSIC_ARCHIVE_URL": "", "MUSIC_ARCHIVE_VARIABLE_PRESENT": "true",
                                    "GITHUB_REPOSITORY": "owner/repo", "GITHUB_ACTIONS": "false"}), \
                patch.object(music.subprocess, "run", return_value=subprocess.CompletedProcess(
                    [], 0, json.dumps({"value": "variable-url"}))), contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual("variable-url", music.archive_url())
            self.assertEqual("", output.getvalue())

    def test_invalid_configuration_returns_sanitized_error_without_traceback(self):
        with patch.dict(os.environ, {"MUSIC_ARCHIVE_URL": "not-a-url", "MUSIC_ARCHIVE_VARIABLE_PRESENT": "false"}), \
                contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(1, music.main())
            self.assertIn("::error::", output.getvalue())
            self.assertNotIn("not-a-url", output.getvalue())
            self.assertNotIn("Traceback", output.getvalue())


if __name__ == "__main__":
    unittest.main()
