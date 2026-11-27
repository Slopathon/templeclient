package nz.temple.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricsTest {
	@Test
	void parsesLinesInOrderAndFindsTheCurrentOne() {
		Lyrics l = Lyrics.parse("[00:12.50] second\n[00:02.00] first line here\n[00:20.00]\n[00:21.10] after the gap", 60);
		assertEquals(4, l.lines().size());
		assertEquals("first line here", l.lines().get(0).text());
		assertNull(l.lineAt(1.0), "nothing before the first line");
		assertEquals("first line here", l.lineAt(3).text());
		assertEquals("second", l.lineAt(12.6).text());
		assertNull(l.lineAt(20.5), "an empty line is an instrumental gap");
		assertEquals("after the gap", l.lineAt(21.5).text());
	}

	@Test
	void wordsAreSpreadAcrossTheLineAndLongerWordsTakeLonger() {
		Lyrics.Line line = Lyrics.line("I extraordinarily go", 10, 14);
		assertEquals(3, line.words().size());
		assertEquals(10, line.words().get(0).start(), 1e-9);
		double shortWord = line.words().get(0).end() - line.words().get(0).start();
		double longWord = line.words().get(1).end() - line.words().get(1).start();
		assertTrue(longWord > shortWord * 3);
		assertTrue(line.words().get(2).end() <= 14);
		assertEquals(-1, line.wordAt(9.9));
		assertEquals(0, line.wordAt(10.01));
		assertEquals(2, line.wordAt(13.9));
	}

	@Test
	void aLongSilenceDoesNotKeepTheLineLit() {
		Lyrics l = Lyrics.parse("[00:01.00] short\n[00:40.00] much later", 0);
		assertNull(l.lineAt(20), "the first line has faded long before the second starts");
	}

	@Test
	void enhancedLrcKeepsItsOwnWordTimes() {
		Lyrics l = Lyrics.parse("[00:05.00] <00:05.00> hello <00:06.50> there <00:08.00> friend\n[00:12.00] next", 30);
		Lyrics.Line line = l.lines().get(0);
		assertEquals("hello there friend", line.text());
		assertEquals(6.5, line.words().get(1).start(), 1e-9);
		assertEquals(8.0, line.words().get(2).start(), 1e-9);
	}

	@Test
	void repeatedTimestampsShareOneLine() {
		Lyrics l = Lyrics.parse("[00:01.00][00:30.00] chorus", 60);
		assertEquals(2, l.lines().size());
		assertEquals("chorus", l.lineAt(30.5).text());
	}

	@Test
	void cleansTitlesAndArtistsForLookup() {
		assertEquals("Hey Jude", Lyrics.cleanTitle("Hey Jude - Remastered 2015"));
		assertEquals("Song", Lyrics.cleanTitle("Song (feat. Someone)"));
		assertEquals("Song", Lyrics.cleanTitle("Song [Official Video]"));
		assertEquals("Doublë", Lyrics.cleanTitle("Doublë"));
		assertEquals("YEAT - MAD BOUT THAT", Lyrics.cleanTitle("YEAT - MAD BOUT THAT ( Official Music Video ) [ Created by @MOSHPXT ]"));
		assertEquals("Yeat", Lyrics.cleanArtist("Yeat - Topic"));
		assertEquals("Drake", Lyrics.cleanArtist("Drake, 21 Savage"));
	}
}
