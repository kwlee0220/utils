package utils.io;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.annotation.concurrent.GuardedBy;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SoundPlay {
	private static final Logger s_logger = LoggerFactory.getLogger("SOUNDPLAY");
	
	public static final int STATE_MEDIA_UNKNOWN = 0;
	public static final int STATE_READY = 1;
	public static final int STATE_PLAYING = 2;
	public static final int STATE_PAUSED = 3;
	public static final int STATE_STOP_REQUESTED = 4;
	public static final int STATE_FINISHED = 5;

	private volatile AudioInputStream m_ais;
	private volatile AudioFormat m_format;
	private volatile long m_startTimeMillis = 0;
	@GuardedBy("this") private long m_currentInBytes;
	@GuardedBy("this") private int m_state;
	
	private SourceDataLine m_line;
	private FloatControl m_volume;
	private BooleanControl m_mute;
	
	public SoundPlay() {
		m_state = STATE_MEDIA_UNKNOWN;
	}
	
	public synchronized void setPlayMedia(File file) throws UnsupportedAudioFileException,
														IOException, LineUnavailableException {
		if ( m_state == STATE_STOP_REQUESTED ) {
			return;
		}
		if ( m_state > STATE_READY ) {
			throw new IllegalStateException("play has been started already");
		}
		
		setPlayMediaInGuard(AudioSystem.getAudioInputStream(file));
	}
	
	public synchronized void setPlayMedia(URL url) throws LineUnavailableException,
												UnsupportedAudioFileException, IOException {
		if ( m_state == STATE_STOP_REQUESTED ) {
			return;
		}
		if ( m_state > STATE_READY ) {
			throw new IllegalStateException("play has been started already");
		}
		
		setPlayMediaInGuard(AudioSystem.getAudioInputStream(url));
	}
	
	public synchronized void setPlayMedia(InputStream is) throws UnsupportedAudioFileException,
														IOException, LineUnavailableException {
		if ( m_state == STATE_STOP_REQUESTED ) {
			return;
		}
		if ( m_state > STATE_READY ) {
			throw new IllegalStateException("play has been started already");
		}
		
		setPlayMediaInGuard(AudioSystem.getAudioInputStream(is));
	}
		
	public synchronized void setPlayMedia(AudioInputStream ais) throws LineUnavailableException {
		if ( m_state == STATE_STOP_REQUESTED ) {
			return;
		}
		if ( m_state > STATE_READY ) {
			throw new IllegalStateException("play has been started already");
		}
		
		setPlayMediaInGuard(ais);
	}
	
	public synchronized void setPcmPlayMedia(InputStream pcmStream, int sampleRate, int sampleSize)
		throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		if ( m_state == STATE_STOP_REQUESTED ) {
			return;
		}
		if ( m_state > STATE_READY ) {
			throw new IllegalStateException("play has been started already");
		}
		
		AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
												sampleRate, sampleSize, 1, 2, sampleRate, false);
		setPlayMediaInGuard(new AudioInputStream(pcmStream, format, AudioSystem.NOT_SPECIFIED));
	}

	public void play() throws IOException, InterruptedException, IllegalStateException,
								LineUnavailableException {
		synchronized ( this ) {
			if ( m_state == STATE_MEDIA_UNKNOWN ) {
				throw new IllegalStateException("media not specified");
			}
			else if ( m_state == STATE_STOP_REQUESTED ) {
				if ( m_ais != null ) {
					try { m_ais.close(); } catch ( IOException e ) { }
				}
		        m_state = STATE_FINISHED;
				return;
			}
			else if ( m_state != STATE_READY ) {
				throw new IllegalStateException("play already started");
			}
			
			m_state = STATE_PLAYING;
		}
		
		try {
			rawPlay();
		}
		finally {
	        try { m_ais.close(); } catch ( IOException e ) { }
		}
	}

	public synchronized void stop() {
		if ( m_state != STATE_FINISHED ) {
			setState(STATE_STOP_REQUESTED);
		}
	}
	
	public synchronized long getStartTimeInMillis() {
		return m_startTimeMillis;
	}
	
	public synchronized void setStartTimeInMillis(long startTime) throws IllegalStateException,
																			IllegalStateException {
		if ( startTime < 0 ) {
			throw new IllegalStateException("invalid StartTimeInMillis=" + startTime);
		}
		if ( m_state >= STATE_PLAYING ) {
			throw new IllegalStateException("play already started");
		}
		
		m_startTimeMillis = startTime;
	}
	
	public synchronized long getCurrentTimeInMillis() {
		if ( m_state < STATE_PLAYING ) {
			return m_startTimeMillis;
		}
		else {
			return (m_currentInBytes * 1000)/((int)m_format.getSampleRate() * m_format.getFrameSize());
		}
	}
	
	public synchronized int getPlayState() {
		return m_state;
	}
	
	public float getVolume() {
		return m_volume.getValue();
	}
	
	public void setVolume(float to) {
		to = Math.min(Math.max(to, -1.0f), 1.0f);
		
		if ( to == -1f ) {
			to = m_volume.getMinimum();
		}
		else if ( to < 0 ) {
			to = -m_volume.getMinimum() * to * .3f;
		}
		else {
			to = m_volume.getMaximum() * to;
		}
		
		m_volume.setValue(to);
	}

	public boolean getMute() {
		return m_mute.getValue();
	}

	public void setMute(boolean mute) {
		m_mute.setValue(mute);
	}
	
	public synchronized boolean isPaused() {
		return m_state == STATE_PAUSED;
	}
	
	public synchronized void pause() throws IllegalStateException {
		if ( m_state == STATE_PLAYING ) {
			setState(STATE_PAUSED);
		}
		else {
			throw new IllegalStateException("not in playing state");
		}
	}
	
	public synchronized void resume() throws IllegalStateException {
		if ( m_state == STATE_PAUSED ) {
			setState(STATE_PLAYING);
		}
		else {
			throw new IllegalStateException("not in paused state");
		}
	}
		
	private void setPlayMediaInGuard(AudioInputStream ais) throws LineUnavailableException {
		AudioFormat format = ais.getFormat();
		if ( format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED ) {
			AudioFormat decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 
														format.getSampleRate(),
												        16,
												        format.getChannels(),
												        format.getChannels() * 2,
												        format.getSampleRate(),
												        false);

			format = decodedFormat;
			m_ais = AudioSystem.getAudioInputStream(format, ais);
		}
		else {
			m_ais = ais;
		}
		m_format = format;
  
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, m_format);
		if ( !AudioSystem.isLineSupported(info) ) {
			throw new RuntimeException("Unsupported AudioStream=" + info);
		}
 
		m_line = (SourceDataLine)AudioSystem.getLine(info);
		m_line.open(m_format);
		
		if( m_line.isControlSupported(FloatControl.Type.MASTER_GAIN) ) {
			m_volume = (FloatControl)m_line.getControl(FloatControl.Type.MASTER_GAIN);
			m_volume.setValue(0.0F);
		}
		else {
			m_volume = null;
		}
		
		if( m_line.isControlSupported(BooleanControl.Type.MUTE) ) {
			m_mute = (BooleanControl)m_line.getControl(BooleanControl.Type.MUTE);
		}
		else {
			m_mute = null;
		}
		
		m_state = STATE_READY;
	}
	
	private void rawPlay() throws LineUnavailableException, IOException, InterruptedException {
		final int fs = m_format.getFrameSize();
		final int halfSecondSize = ((int)m_format.getSampleRate() * fs) / 2;
		final int writeBufferSize = halfSecondSize / 10;
		byte[] buffer = new byte[halfSecondSize];
		
		// 시작 play 시간이 정해져 있는 경우는 해당 분량만큼의 데이타를 그냥 읽어서 버린다.
		long startTimeMillis = m_startTimeMillis;
		if ( startTimeMillis > 0 ) {
			long offset = (halfSecondSize * startTimeMillis) / 500;
			synchronized ( this ) {
				m_currentInBytes = (offset/fs) * fs;	// frame 크기로 align 시킴
			}
			
			while ( offset > 0 ) {
				long remains = Math.min(offset, buffer.length);
				int nread = m_ais.read(buffer, 0, (int)remains);
				if ( nread < 0 ) {
					return;
				}
				
				offset -= nread;
			}
		}

		m_line.open(m_format);
		try {
			m_line.start();
			
			int offset = 0;
			int remains = 0;
			while ( true ) {
				if ( remains == 0 ) {
					remains = m_ais.read(buffer, 0, buffer.length);
					if ( remains < 0 ) {
						break;
					}
					offset = 0;
				}
				
				int nbytes = Math.min(writeBufferSize, remains);
				int nwritten = m_line.write(buffer, offset, nbytes);
				if ( nwritten < nbytes ) {
					throw new IOException("fails in writing into DataLine=" + m_line);
				}
				
				synchronized ( this ) {
					m_currentInBytes += nwritten;
					
					while ( m_state == STATE_PAUSED ) {
						try {
							this.wait();
						}
						catch ( InterruptedException e ) {
							Thread.currentThread().interrupt();
							throw e;
						}
					}
					
					if ( m_state >= STATE_STOP_REQUESTED ) {
						if ( s_logger.isInfoEnabled() ) {
							s_logger.info("stopped: " + this);
						}
						
						break;
					}
				}
				
				remains -= nwritten;
				offset += nwritten;
			}
		}
		finally {
			m_line.drain();
			m_line.close();
			
			setState(STATE_FINISHED);
		}
	}
	
	private synchronized void setState(int state) {
		m_state = state;
		this.notifyAll();
	}
}