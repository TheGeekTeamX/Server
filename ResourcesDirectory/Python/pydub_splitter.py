from pydub import AudioSegment
from pydub.silence import split_on_silence
import pydub
import sys



def splitAudio(path, dest):
    song = AudioSegment.from_wav(path)
    song.set_frame_rate(16000)
    average_sound = song.dBFS

    chuncs = split_on_silence(song, min_silence_len=450, silence_thresh=average_sound - 4)
    parts = pydub.silence.detect_nonsilent(song, min_silence_len=450, silence_thresh=average_sound - 4)
    # print(song.duration_seconds)
    i=0
    total_duration = song.duration_seconds
    for p in parts:
        if (p[0] - 600) / 1000 < 0:
            start = 0
        else:
            start = p[0] - 600
        if ((p[1] + 600) / 1000) <= total_duration:
            end = p[1] + 600
        else:
            end = total_duration * 1000
            print(end)
        part = song[start:end]
        dest_temp=dest+str(i)+"-"
        i=i+1
        part.export(dest_temp+str((start+600) / 1000) + "-" + str((end-600) / 1000) + ".wav", format="wav")


if __name__ == "__main__":
    if len(sys.argv) >= 3:
        path = sys.argv[1]
        dest = sys.argv[2]
        splitAudio(path=path, dest=dest)

    else:
        print("no arguments , need to put first audio path and second dest folder")
