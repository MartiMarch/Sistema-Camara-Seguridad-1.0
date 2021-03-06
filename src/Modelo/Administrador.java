package Modelo;

import ClasesAuxiliares.ArchivoConfiguracion;
import ClasesAuxiliares.Encriptador;
import Vista.AlarmasRecibidas;
import Vista.ReproducirVideo;
import Vista.VisualizarCamara;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import org.opencv.core.Core;
import org.opencv.videoio.VideoCapture;

public class Administrador{
    static
    {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        System.loadLibrary("opencv_java451");
        System.loadLibrary("opencv_videoio_ffmpeg451_64");
    }
    
    private String contraseña;
    private String email;
    private String contraseñaCorreo;
    private String ruta;
    private ArchivoConfiguracion archivo = new ArchivoConfiguracion();
    private Encriptador encriptador = new Encriptador();
    
    public Administrador(){}
    
    public Administrador(String contraseña, String email, String contraseñaCorreo, String ruta)
    {
        this.contraseña = contraseña + archivo.consultarParametro("salt");
        this.email = email;
        this.contraseñaCorreo = contraseñaCorreo;
        this.ruta = ruta;
    }
    
    public Administrador registrarAdministrador(String contraseña, String correo, String contraseñaCorreo)
    {
        Administrador administrador = new Administrador(contraseña, correo, contraseñaCorreo, "C:\\SGCSR");
        ArrayList<String> hash = encriptador.generarHash(contraseña);
        contraseña += hash.get(1);
        
        this.contraseña = contraseña;
        this.email = correo;
        this.contraseñaCorreo = contraseñaCorreo;
        this.ruta = "C:\\SGCSR";
        
        archivo.guardarParametro("contraseña", hash.get(0));
        archivo.guardarParametro("salt", hash.get(1));
        correo = encriptador.encriptar(correo, contraseña);
        archivo.guardarParametro("email", correo);
        contraseñaCorreo = encriptador.encriptar(contraseñaCorreo, contraseña);
        archivo.guardarParametro("contraseñaCorreo", contraseñaCorreo);
        archivo.guardarParametro("ruta", "C:\\SGCSR");
        
        return administrador;
    }
    
    public boolean iniciarSesionAdministrador(String contraseña)
    {
        boolean identificado = false;

        ArchivoConfiguracion archivo = new ArchivoConfiguracion();
        String contraseñaCifrada = archivo.consultarParametro("contraseña");
        String salt = archivo.consultarParametro("salt");
        contraseña += salt;
        if(contraseña.equals(encriptador.desencriptar(contraseñaCifrada, contraseña)))
        {
            String email = archivo.consultarParametro("email");
            email = encriptador.desencriptar(email, contraseña);
            String contraseñaCorreo = archivo.consultarParametro("contraseñaCorreo");
            contraseñaCorreo = encriptador.desencriptar(contraseñaCorreo, contraseña);
            String ruta = archivo.consultarParametro("ruta");
         
            this.contraseña = contraseña;
            this.email = email;
            this.contraseñaCorreo = contraseñaCorreo;
            this.ruta = ruta;
            
            identificado = true;
        }
        
        return identificado;
    }
    
    public boolean insertarCamara(String url, Movimiento movimiento, ArrayList<Cliente> clientes) throws ClassNotFoundException, SQLException, InterruptedException
    {
        boolean insertado = true;
        Pattern rtsp_patron = Pattern.compile("rtsp://((([a-zA-Z0-9_]+):([a-zA-Z0-9_]+)@)?)(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3}).(\\d{1,3})(.*)");
        Pattern http_patron = Pattern.compile("http://(.*)");
        Pattern https_patron = Pattern.compile("https://(.*)");
        Matcher matcher_rstp = rtsp_patron.matcher(url);
        Matcher matcher_http = http_patron.matcher(url);
        Matcher matcher_https = https_patron.matcher(url);
        if(!matcher_rstp.matches() && !matcher_http.matches() && !matcher_https.matches())
        {
            JOptionPane.showMessageDialog(null, "La URL introducida no tiene el formato esperado.");
            insertado = false;
        }
        
        if(insertado)
        {
            VideoCapture camara = new VideoCapture(url);
            if(camara.isOpened())
            {
                camara.release();
            }
            else
            {
                JOptionPane.showMessageDialog(null, "Ninguna cámara tiene asociada la URL introducida.");
                insertado = false;
            }
        }
        
        if(insertado)
        {
            Statement st = conexion().createStatement();
            String existeCamara = "SELECT * FROM camaras WHERE url = '" + url + "'";
            ResultSet rs = st.executeQuery(existeCamara);
            int numeroCamaras = 0;
            while(rs.next())
            {
                ++numeroCamaras;
            }
            if(numeroCamaras==0)
            {
                url = encriptador.encriptar(url, contraseña);
                movimiento.addCamra(url);
                String insertarCamara = "INSERT INTO camaras VALUES ('" + url + "')";
                st.executeUpdate(insertarCamara);
                String asociarCamCli = null;
                for(int i = 0; i < clientes.size(); ++i)
                {
                    asociarCamCli = "INSERT INTO camarasclientes VALUES(NULL, '" + clientes.get(i).getNombre() + "', '" + url + "', 'RENOMBRAR', 'ACTIVADA');";
                    st.executeUpdate(asociarCamCli);
                }
            }
            else
            {
                insertado = false;
            }
            rs.close();
            st.close();
        }
        
        return insertado;
    }
    
    public void eliminarCamara(String url, Movimiento movimiento) throws SQLException, ClassNotFoundException, InterruptedException
    {
        url = encriptador.encriptar(url, contraseña);
        Statement st = conexion().createStatement();
        String eliminarAsociaciones = "DELETE FROM camarasclientes WHERE urlCamara = '" + url + "'";
        st.executeUpdate(eliminarAsociaciones);
        String eliminarCamara = "DELETE FROM camaras WHERE url = '" + url + "'";
        st.executeUpdate(eliminarCamara);
        st.close();
        movimiento.finalizarHilos(url);
    }
    
    public void visualizarCamara(String url) throws InterruptedException
    {
        VisualizarCamara vc = new VisualizarCamara(url);
        vc.setVisible(true);
    }

    public String getContraseña()
    {
        return contraseña;
    }

    public String getEmail()
    {
        return email;
    }

    public String getContraseñaCorreo()
    {
        return contraseñaCorreo;
    }

    public String getRuta()
    {
        return ruta;
    }
    
    public Connection conexion() throws SQLException, ClassNotFoundException
    {
        Class.forName("com.mysql.jdbc.Driver");
        Connection conexion = DriverManager.getConnection("jdbc:mysql://localhost:3306/sgcsr","root","3+UNO=cuatro");
        
        return conexion;
    }
    
    public boolean modificarCorreo(String correo, String contraseña1, String contraseña2, String contraseñaEnviadaCorreo, String contraseñaEnviadaCorreoCorrecta)
    {
        boolean datosCorrectos = false;
        Pattern patronCorreo = Pattern.compile("([a-zA-Z0-9_\\.]+)@([a-zA-Z0-9_\\.]+)\\.([a-zA-Z0-9_\\.]+)");
        Matcher matcherCorreo = patronCorreo.matcher(correo);
        if(contraseña1.equals(contraseña2) && contraseña1.length() > 7 && matcherCorreo.matches() && contraseñaEnviadaCorreoCorrecta.equals(contraseñaEnviadaCorreo))
        {
            archivo.guardarParametro("email", encriptador.encriptar(correo, contraseña));
            archivo.guardarParametro("contraseñaCorreo", encriptador.encriptar(contraseña1, contraseña));
        }
        else
        {
            JOptionPane.showMessageDialog(null,"El correo y/o la contraseña son icnorrectos.");
        }
        
        return datosCorrectos;
    }
    
    public boolean modificarContaseña(String contraseña1, String contraseña2, String contraseñaEnviadaCorreoCorrecta, String contraseñaEnviadaCorreo)
    {
        boolean datosCorrectos = false;
        if(contraseña1.equals(contraseña2) && contraseña1.length() > 7 && contraseñaEnviadaCorreoCorrecta.equals(contraseñaEnviadaCorreo))
        {
            datosCorrectos = true;
            
            ArrayList<String> hash = encriptador.generarHash(contraseña1);
            contraseña = contraseña1 + hash.get(1);
            archivo.guardarParametro("contraseña", hash.get(0));
            archivo.guardarParametro("salt", hash.get(1));
            archivo.guardarParametro("email", encriptador.encriptar(this.email, contraseña));
            archivo.guardarParametro("contraseñaCorreo", encriptador.encriptar(this.contraseñaCorreo, contraseña));
        }
        else
        {
            JOptionPane.showMessageDialog(null,"El formato y/o el número del correo son incorrectos.");
        }
        
        return datosCorrectos;
    }
    
    public boolean añadirCliente(String nombre, String correo, String contraseña1, String contraseña2, ArrayList<Camara> camaras) throws SQLException, ClassNotFoundException
    {
        boolean datosCorrectos = false;
        Pattern patronCorreo = Pattern.compile("([a-zA-Z0-9_\\.]+)@([a-zA-Z0-9_\\.]+)\\.([a-zA-Z0-9_\\.]+)");
        Matcher matcherCorreo = patronCorreo.matcher(correo);
        if(contraseña1.equals(contraseña2) && contraseña1.length() > 7 && matcherCorreo.matches())
        {
            datosCorrectos = true;
            
            ArrayList<String> hash = encriptador.generarHash(contraseña1);
            correo = encriptador.encriptar(correo, contraseña);
            
            Statement st = conexion().createStatement();
            String insertarCliente = "INSERT INTO clientes VALUES ('" + nombre + "', '" + hash.get(0) + "', '" + hash.get(1) + "', '" + correo + "');";
            st.executeUpdate(insertarCliente);
            st.close();
            st = conexion().createStatement();
            for(int i = 0; i < camaras.size(); ++i)
            {
                String asociarCamaraCliente = "INSERT INTO camarasclientes VALUES (NULL, '" + nombre + "', '" + encriptador.encriptar(camaras.get(i).getURL(), contraseña) + "', 'RENOMBRAR', 'ACTIVADA')";
                st.executeUpdate(asociarCamaraCliente);
            }
            st.close();
        }
        else
        {
            JOptionPane.showMessageDialog(null,"El correo y/o la contraseña son icnorrectos.");
        }

        return datosCorrectos;
    }
    
    public void eliminarCliente(String nombre) throws SQLException, ClassNotFoundException
    {
        Statement st = conexion().createStatement();
        String eliminarCliente = "DELETE FROM camarasclientes WHERE nombreCliente = '" + nombre + "'";
        st.executeUpdate(eliminarCliente);
        eliminarCliente = "DELETE FROM alarmasclientes WHERE nombreCliente = '" + nombre + "'";
        st.executeUpdate(eliminarCliente);
        eliminarCliente = "DELETE FROM clientes WHERE nombre = '" + nombre + "'";
        st.executeUpdate(eliminarCliente);
        st.close();
    }
    
    public void eliminarVideo(Video video) throws SQLException, ClassNotFoundException, ParseException
    {
        String fechaVideo = video.getId();
        fechaVideo = fechaVideo.replaceAll("-", "/");
        fechaVideo = fechaVideo.replaceAll("_", ":");
        SimpleDateFormat fechaF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date d = null;
        d = fechaF.parse(fechaVideo);
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime mes = now.plusDays(-30);
        if(d.toInstant().isBefore(mes.toInstant()))
        {
            Statement st = conexion().createStatement();
            String eliminarVideo = "DELETE FROM videos WHERE id = '" + video.getId() + "'";
            st.executeUpdate(eliminarVideo);
            st.close();
            File archivo = new File("C:\\SGCSR\\" + video.getId() + ".mp4");
            archivo.delete();
        }
        else
        {
            JOptionPane.showMessageDialog(null,"No se puede borrar el vídeo ya que no tiene un antigüedad superior a un mes.");
        }
    }
    
    public void visualizarVideo(String id) throws InterruptedException
    {
        String ruta = "C:\\SGCSR\\" + id + ".mp4";
        ReproducirVideo rv = new ReproducirVideo(ruta);
        rv.setVisible(true);
    }
    
    public ArrayList<Video> buscarVideo(String fechaInicial_dia, String fechaInicial_mes, String fechaInicial_año, String fechaFinal_dia, String fechaFinal_mes, String fechaFinal_año, ArrayList<Video> videos) throws ParseException, SQLException, ClassNotFoundException
    {
        ArrayList<Video> videosAceptados = new ArrayList();
        if(fechaInicial_dia.equals(""))
        {
            fechaInicial_dia = "01";
        }
        if(fechaInicial_mes.equals(""))
        {
            fechaInicial_mes = "01";
        }
        if(fechaInicial_año.equals(""))
        {
            fechaInicial_año = "2021";
        }
        if(fechaFinal_dia.equals(""))
        {
            fechaFinal_dia = "01";
        }
        if(fechaFinal_mes.equals(""))
        {
            fechaFinal_mes = "01";
        }
        if(fechaFinal_año.equals(""))
        {
            fechaFinal_año = "2021";
        }
        String fechaInicial = fechaInicial_dia + "-" + fechaInicial_mes + "-" + fechaInicial_año;
        String fechaFinal = fechaFinal_dia + "-" + fechaFinal_mes + "-" + fechaFinal_año;
        if(validarFecha(fechaInicial, fechaInicial_dia, fechaInicial_mes) && validarFecha(fechaFinal, fechaFinal_dia, fechaFinal_mes))
        {
            Date dateFechaInicial = new SimpleDateFormat("dd-MM-yyyy").parse(fechaInicial);
            Date dateFechaFinal = new SimpleDateFormat("dd-MM-yyyy").parse(fechaFinal);
            for(int i = 0; i < videos.size(); ++i)
            {
                String fechaVideo = videos.get(i).getDia() + "-" + videos.get(i).getMes() + "-" + videos.get(i).getAño();
                Date dateFechaVideo = new SimpleDateFormat("dd-MM-yyyy").parse(fechaVideo);
                if(dateFechaVideo.after(dateFechaInicial) && dateFechaVideo.before(dateFechaFinal))
                {
                    videosAceptados.add(videos.get(i));
                }
            }
        }
        else
        {
            JOptionPane.showMessageDialog(null, "Las fechas introducidas son incorrectas.");
        }
        return videosAceptados;
    }
    
    public boolean validarFecha(String fecha, String dia, String mes)
    {
        try{
            DateFormat df = new SimpleDateFormat("dd-MM-yyyy");
            df.setLenient(false);
            df.parse(fecha);
            boolean validacion = false;
            int diaNum = Integer.parseInt(dia);
            switch(mes)
            {
                case "01":
                    if(diaNum >= 1 && diaNum <= 31)
                    {
                        validacion = true;
                    }
                    break;
                case "02":
                    if(diaNum >= 1 && diaNum <= 28)
                    {
                        validacion = true;
                    }
                    break;
                case "03":  
                    if(diaNum >= 1 && diaNum <= 31)
                    {
                        validacion = true;
                    }
                    break;
                case "04":
                    if(diaNum >= 1 && diaNum <= 30)
                    {
                        validacion = true;
                    }
                    break;
                case "05":
                    if(diaNum >= 1 && diaNum <= 31)
                    {
                        validacion = true;
                    }
                    break;
                case "06":
                    if(diaNum >= 1 && diaNum <= 30)
                    {
                        validacion = true;
                    }
                    break;
                case "07":
                    if(diaNum >= 1 && diaNum <= 31)
                        
                    {
                        validacion = true;
                    }
                    break;
                case "08":
                    if(diaNum >= 1 && diaNum <= 31)
                    {
                        validacion = true;
                    }
                    break;
                case "09":
                    if(diaNum >= 1 && diaNum <= 30)
                    {
                        validacion = true;
                    }
                    break;
                case "10":
                    if(diaNum >= 1 && diaNum <= 31)
                    {
                        validacion = true;
                    }
                    break;
                case "11":
                    if(diaNum >= 1 && diaNum <= 30)
                    {
                        validacion = true;
                    }
                    break;
                case "12":
                    if(diaNum >= 1 && diaNum <= 31)
                    {
                        validacion = true;
                    }
                    break;
                default:
                    break;
            }
            
            return validacion;
        }
        catch(ParseException e)
        {
            return false;
        }
    }
    
    public void VisualizarClientesAlarmas(ArrayList<String> clientes)
    {
        AlarmasRecibidas ar = new AlarmasRecibidas(clientes);
        ar.setVisible(true);
    }
    
    public void cambiarAlgoritmo(Movimiento movimiento, boolean seleccion)
    {
        movimiento.cambiarAlgorimto(seleccion);
    }
}