using System;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Net;
using System.Threading;
using System.Threading.Tasks;

// Local archive reader; no dependency on tar.exe, curl.exe or a global runtime.
public static class SmartChargeArchive {
    // Small byte ranges avoid the archive server's very slow full-file stream.
    // Four bounded workers only; the caller still checks the full upstream hash.
    public static void DownloadRanges(string url,string file,long length) {
        const int chunk=1024*1024;
        int count=(int)((length+chunk-1)/chunk);
        string parts=file+".parts";
        Directory.CreateDirectory(parts);
        Parallel.For(0,count,new ParallelOptions{MaxDegreeOfParallelism=4},i=>{
            long first=(long)i*chunk,last=Math.Min(length-1,first+chunk-1),want=last-first+1;
            string part=Path.Combine(parts,i.ToString("D5")+".part");
            if(File.Exists(part)&&new FileInfo(part).Length==want)return;
            for(int attempt=0;;attempt++){
                try{
                    var req=(HttpWebRequest)WebRequest.Create(url);req.AddRange(first,last);
                    req.Timeout=30000;req.ReadWriteTimeout=30000;req.UserAgent="SmartCharge-Park-Setup/1";
                    req.ServicePoint.ConnectionLimit=4;
                    using(var response=(HttpWebResponse)req.GetResponse()){
                        if(response.ResponseUri.Scheme!="https"||response.StatusCode!=HttpStatusCode.PartialContent||
                           response.Headers["Content-Range"]!="bytes "+first+"-"+last+"/"+length)
                            throw new InvalidDataException("Unexpected official archive range response");
                        using(var input=response.GetResponseStream())using(var output=File.Create(part)){input.CopyTo(output);}
                    }
                    if(new FileInfo(part).Length!=want)throw new InvalidDataException("Incomplete archive range");
                    break;
                }catch{if(File.Exists(part))File.Delete(part);if(attempt>=2)throw;Thread.Sleep(1000);}
            }
        });
        using(var output=File.Create(file))for(int i=0;i<count;i++){
            using(var input=File.OpenRead(Path.Combine(parts,i.ToString("D5")+".part"))){input.CopyTo(output);}
        }
        for(int i=0;i<count;i++)File.Delete(Path.Combine(parts,i.ToString("D5")+".part"));
        Directory.Delete(parts,false);
    }
    static string Safe(string root, string name) {
        string path=Path.GetFullPath(Path.Combine(root,name.Replace('/',Path.DirectorySeparatorChar)));
        if(!path.Equals(Path.GetFullPath(root).TrimEnd('\\'),StringComparison.OrdinalIgnoreCase) && !path.StartsWith(Path.GetFullPath(root).TrimEnd('\\')+"\\",StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("Archive entry escapes extraction root");
        return path;
    }
    static int Fill(Stream s,byte[] b,int length) {int n=0;while(n<length){int r=s.Read(b,n,length-n);if(r==0)break;n+=r;}return n;}
    static string Text(byte[] b,int offset,int length){return Encoding.UTF8.GetString(b,offset,length).TrimEnd('\0',' ');}
    public static void Extract(string file,string root,bool gzipTar) {
        Directory.CreateDirectory(root);
        if(!gzipTar){using(var z=ZipFile.OpenRead(file))foreach(var e in z.Entries){
            if(e.FullName.EndsWith("/")){Directory.CreateDirectory(Safe(root,e.FullName));continue;}
            string dest=Safe(root,e.FullName);Directory.CreateDirectory(Path.GetDirectoryName(dest));e.ExtractToFile(dest,true);
        }return;}
        using(var f=File.OpenRead(file))using(var gz=new GZipStream(f,CompressionMode.Decompress)){
            byte[] header=new byte[512];byte[] buffer=new byte[65536];string longName=null;
            while(Fill(gz,header,512)==512){
                bool zero=true;foreach(byte b in header)if(b!=0){zero=false;break;}if(zero)break;
                string name=Text(header,0,100),prefix=Text(header,345,155);if(prefix.Length>0)name=prefix+"/"+name;
                long size=Convert.ToInt64(Text(header,124,12).Trim(),8);char type=(char)header[156];
                if(longName!=null){name=longName;longName=null;}
                Stream output=null;MemoryStream metadata=null;
                if(type=='L'||type=='x'||type=='g'){metadata=new MemoryStream();output=metadata;}
                else if(type=='5'){Directory.CreateDirectory(Safe(root,name));}
                else if(type=='0'||type=='\0'){string dest=Safe(root,name);Directory.CreateDirectory(Path.GetDirectoryName(dest));output=File.Create(dest);}
                else if(type=='1'||type=='2')throw new InvalidDataException("Archive links are not allowed");
                long remain=size;
                try{while(remain>0){int want=(int)Math.Min(remain,buffer.Length);int read=Fill(gz,buffer,want);if(read!=want)throw new EndOfStreamException();if(output!=null)output.Write(buffer,0,read);remain-=read;}}
                finally{if(output!=null && metadata==null)output.Dispose();}
                if(metadata!=null){string value=Encoding.UTF8.GetString(metadata.ToArray()).TrimEnd('\0','\n');if(type=='L')longName=value;
                    else foreach(string line in value.Split('\n')){int k=line.IndexOf(" path=");if(k>=0)longName=line.Substring(k+6);}metadata.Dispose();}
                int padding=(int)((512-size%512)%512);if(padding>0&&Fill(gz,buffer,padding)!=padding)throw new EndOfStreamException();
            }
        }
    }
}
