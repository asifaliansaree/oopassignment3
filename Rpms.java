package rpms;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jakarta.mail.*;
import jakarta.mail.internet.*;

// Custom Exceptions
class RpmsException extends Exception {
    private final String errorCode;

    public RpmsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RpmsException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void log(Logger logger) {
        String logMessage = String.format(
            "RpmsException [Code: %s]: %s%s",
            errorCode, getMessage(), getCause() != null ? ", Cause: " + getCause().getMessage() : ""
        );
        logger.severe(logMessage);
    }
}

// User Management Classes
abstract class User {
    private String userID;
    private String userName;
    private String contactInfo;
    private String gender;

    public User(String userID, String userName, String contactInfo, String gender) throws RpmsException {
        if (userID == null || userID.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "User ID cannot be null or empty");
        }
        if (userName == null || userName.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "User name cannot be null or empty");
        }
        if (contactInfo == null || !contactInfo.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new RpmsException("INVALID_INPUT", "Invalid contact info (must be a valid email)");
        }
        if (gender == null || gender.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Gender cannot be null or empty");
        }
        this.userID = userID;
        this.userName = userName;
        this.contactInfo = contactInfo;
        this.gender = gender;
    }

    public String getUserID() { return userID; }
    public String getName() { return userName; }
    public String getContactInfo() { return contactInfo; }
    public String getGender() { return gender; }

    public void setUserID(String userID) throws RpmsException {
        if (userID == null || userID.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "User ID cannot be null or empty");
        }
        this.userID = userID;
    }

    public void setName(String userName) throws RpmsException {
        if (userName == null || userName.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "User name cannot be null or empty");
        }
        this.userName = userName;
    }

    public void setContactInfo(String contactInfo) throws RpmsException {
        if (contactInfo == null || !contactInfo.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new RpmsException("INVALID_INPUT", "Invalid contact info (must be a valid email)");
        }
        this.contactInfo = contactInfo;
    }

    public void setGender(String gender) throws RpmsException {
        if (gender == null || gender.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Gender cannot be null or empty");
        }
        this.gender = gender;
    }

    @Override
    public String toString() {
        return String.format("User ID: %s\nUser Name: %s\nContact Info: %s\nGender: %s",
                userID, userName, contactInfo, gender);
    }
}

class Patient extends User {
    private LocalDate birthDate;
    private LocalDate admissionDate;
    private LocalDateTime appointmentDateTime;
    private MedicalHistory medicalHistory;
    private ChatClient chatClient;

    public Patient(String userID, String userName, String contactInfo, String gender, LocalDate birthDate) throws RpmsException {
        super(userID, userName, contactInfo, gender);
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Birth date must be a valid past date");
        }
        this.birthDate = birthDate;
        this.medicalHistory = new MedicalHistory(this);
    }

    public void setBirthDate(LocalDate birthDate) throws RpmsException {
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Birth date must be a valid past date");
        }
        this.birthDate = birthDate;
    }

    public void setAdmissionDate(LocalDate admissionDate) throws RpmsException {
        if (admissionDate != null && admissionDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Admission date cannot be in the future");
        }
        this.admissionDate = admissionDate;
    }

    public void setAppointmentDateTime(LocalDateTime appointmentDateTime) throws RpmsException {
        if (appointmentDateTime != null && appointmentDateTime.isBefore(LocalDateTime.now())) {
            throw new RpmsException("INVALID_DATE", "Appointment date cannot be in the past");
        }
        this.appointmentDateTime = appointmentDateTime;
    }

    public LocalDate getBirthDate() { return birthDate; }
    public LocalDate getAdmissionDate() { return admissionDate; }
    public LocalDateTime getAppointmentDateTime() { return appointmentDateTime; }
    public MedicalHistory getMedicalHistory() { return medicalHistory; }
    public void setChatClient(ChatClient chatClient) { this.chatClient = chatClient; }

    public void uploadVitals(VitalSign vital, VitalsDatabase database) throws RpmsException {
        if (vital == null) {
            throw new RpmsException("INVALID_INPUT", "Vital sign cannot be null");
        }
        database.addVitalSign(vital);
    }

    public void viewFeedback() {
        List<String> feedback = medicalHistory.getFeedbacks();
        if (feedback.isEmpty()) {
            System.out.println("No feedback available.");
        } else {
            feedback.forEach(System.out::println);
        }
    }

    public void scheduleAppointment(String appointmentID, LocalDateTime time, Doctor doctor, AppointmentManager manager) throws RpmsException {
        if (appointmentID == null || time == null || doctor == null || manager == null) {
            throw new RpmsException("INVALID_INPUT", "Appointment details cannot be null");
        }
        manager.requestAppointment(appointmentID, time, this, doctor);
    }

    public void sendMessage(String receiverID, String message) throws RpmsException {
        if (chatClient == null) {
            throw new RpmsException("CHAT_ERROR", "Chat client not initialized");
        }
        if (receiverID == null || message == null || receiverID.trim().isEmpty() || message.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Receiver ID or message cannot be null or empty");
        }
        chatClient.sendMessage(receiverID, message);
    }

    @Override
    public String toString() {
        return super.toString() + String.format("\nBirth Date: %s\nAdmission Date: %s\nAppointment Date: %s",
                birthDate, admissionDate != null ? admissionDate : "Not set",
                appointmentDateTime != null ? appointmentDateTime : "Not set");
    }
}

class Doctor extends User {
    private LocalDate joiningDate;
    private List<LocalDateTime> availableTime = new ArrayList<>();
    private List<Appointment> appointments = new ArrayList<>();

    public Doctor(String userID, String userName, String contactInfo, String gender, LocalDate joiningDate) throws RpmsException {
        super(userID, userName, contactInfo, gender);
        if (joiningDate == null || joiningDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Joining date must be a valid past or present date");
        }
        this.joiningDate = joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) throws RpmsException {
        if (joiningDate == null || joiningDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Joining date must be a valid past or present date");
        }
        this.joiningDate = joiningDate;
    }

    public void setAvailableTime(LocalDateTime time) throws RpmsException {
        if (time == null || time.isBefore(LocalDateTime.now())) {
            throw new RpmsException("INVALID_DATE", "Available time cannot be in the past");
        }
        availableTime.add(time);
    }

    public LocalDate getJoiningDate() { return joiningDate; }
    public List<LocalDateTime> getAvailableTime() { return Collections.unmodifiableList(availableTime); }

    public void viewPatientVitals(String patientID, VitalsDatabase database) throws RpmsException {
        if (patientID == null || patientID.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Patient ID cannot be null or empty");
        }
        if (database == null) {
            throw new RpmsException("INVALID_INPUT", "Vitals database cannot be null");
        }
        VitalSign latest = database.getLatestVitalSign(patientID);
        if (latest != null) {
            System.out.println(latest);
        } else {
            System.out.println("No vitals found for patient " + patientID);
        }
    }


    public void provideFeedback(String feedback, MedicalHistory history, NotificationService notificationService) throws RpmsException {
        if (feedback == null || feedback.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Feedback cannot be null or empty");
        }
        if (history == null || notificationService == null) {
            throw new RpmsException("INVALID_INPUT", "Medical history or notification service cannot be null");
        }
        history.addFeedback(feedback);
        notificationService.sendEmailAlert(
            history.getPatient().getContactInfo(),
            "New Feedback from Dr. " + getName(),
            feedback
        );
    }

    public void manageAppointment(LocalDateTime time, Patient patient, AppointmentManager manager, String action, NotificationService notificationService) throws RpmsException {
        if (time == null || patient == null || manager == null || action == null) {
            throw new RpmsException("INVALID_INPUT", "Appointment details cannot be null");
        }
        if ("approve".equalsIgnoreCase(action)) {
            manager.approveAppointment(time, patient, this);
            notificationService.sendEmailAlert(
                patient.getContactInfo(),
                "Appointment Approved",
                "Your appointment with Dr. " + getName() + " on " + time + " has been approved."
            );
        } else if ("cancel".equalsIgnoreCase(action)) {
            manager.cancelAppointment(time, patient, this);
            notificationService.sendEmailAlert(
                patient.getContactInfo(),
                "Appointment Cancelled",
                "Your appointment with Dr. " + getName() + " on " + time + " has been cancelled."
            );
        } else {
            throw new RpmsException("INVALID_ACTION", "Action must be 'approve' or 'cancel'");
        }
    }

    public void receiveAlert(String message) {
        System.out.println("Dr. " + getName() + " received alert: " + message);
    }

    @Override
    public String toString() {
        return super.toString() + String.format("\nJoining Date: %s\nAvailable Time: %s",
                joiningDate, availableTime);
    }
}

class Administrator extends User {
    private List<Patient> patients = new ArrayList<>();
    private List<Doctor> doctors = new ArrayList<>();
    private List<String> systemLogs = new ArrayList<>();
    private LocalDate joiningDate;

    public Administrator(String userID, String userName, String contactInfo, String gender, LocalDate joiningDate) throws RpmsException {
        super(userID, userName, contactInfo, gender);
        if (joiningDate == null || joiningDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Joining date must be a valid past or present date");
        }
        this.joiningDate = joiningDate;
    }

    public void setJoiningDate(LocalDate joiningDate) throws RpmsException {
        if (joiningDate == null || joiningDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Joining date must be a valid past or present date");
        }
        this.joiningDate = joiningDate;
    }

    public LocalDate getJoiningDate() { return joiningDate; }

    public void addPatient(Patient patient) throws RpmsException {
        if (patient == null) {
            throw new RpmsException("INVALID_INPUT", "Patient cannot be null");
        }
        patients.add(patient);
        systemLogs.add("Added patient " + patient.getUserID() + " on " + LocalDate.now());
    }

    public void addDoctor(Doctor doctor) throws RpmsException {
        if (doctor == null) {
            throw new RpmsException("INVALID_INPUT", "Doctor cannot be null");
        }
        doctors.add(doctor);
        systemLogs.add("Added doctor " + doctor.getUserID() + " on " + LocalDate.now());
    }

    public void removePatient(Patient patient) throws RpmsException {
        if (patient == null) {
            throw new RpmsException("INVALID_INPUT", "Patient cannot be null");
        }
        patients.remove(patient);
        systemLogs.add("Removed patient " + patient.getUserID() + " on " + LocalDate.now());
    }

    public void removeDoctor(Doctor doctor) throws RpmsException {
        if (doctor == null) {
            throw new RpmsException("INVALID_INPUT", "Doctor cannot be null");
        }
        doctors.remove(doctor);
        systemLogs.add("Removed doctor " + doctor.getUserID() + " on " + LocalDate.now());
    }

    public List<String> getSystemLogs() {
        return new ArrayList<>(systemLogs);
    }

    @Override
    public String toString() {
        return super.toString() + String.format("\nJoining Date: %s", joiningDate);
    }
}

// Health Data Handling Classes
class VitalSign {
    private Patient patient;
    private double heartRate;
    private double bloodPressure;
    private double bodyTemperature;
    private double oxygenLevel;
    private LocalDate checkupDate;

    public VitalSign(Patient patient, double heartRate, double bloodPressure, double bodyTemperature, double oxygenLevel, LocalDate checkupDate) throws RpmsException {
        if (patient == null) {
            throw new RpmsException("INVALID_INPUT", "Patient cannot be null");
        }
        if (checkupDate == null || checkupDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Checkup date must be a valid past or present date");
        }
        this.patient = patient;
        this.heartRate = heartRate;
        this.bloodPressure = bloodPressure;
        this.bodyTemperature = bodyTemperature;
        this.oxygenLevel = oxygenLevel;
        this.checkupDate = checkupDate;
    }

    public Patient getPatient() { return patient; }
    public double getHeartRate() { return heartRate; }
    public double getBloodPressure() { return bloodPressure; }
    public double getBodyTemperature() { return bodyTemperature; }
    public double getOxygenLevel() { return oxygenLevel; }
    public LocalDate getCheckupDate() { return checkupDate; }

    public void setPatient(Patient patient) throws RpmsException {
        if (patient == null) {
            throw new RpmsException("INVALID_INPUT", "Patient cannot be null");
        }
        this.patient = patient;
    }

    public void setHeartRate(double heartRate) { this.heartRate = heartRate; }
    public void setBloodPressure(double bloodPressure) { this.bloodPressure = bloodPressure; }
    public void setBodyTemperature(double bodyTemperature) { this.bodyTemperature = bodyTemperature; }
    public void setOxygenLevel(double oxygenLevel) { this.oxygenLevel = oxygenLevel; }

    public void setCheckupDate(LocalDate checkupDate) throws RpmsException {
        if (checkupDate == null || checkupDate.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Checkup date must be a valid past or present date");
        }
        this.checkupDate = checkupDate;
    }

    @Override
    public String toString() {
        return String.format("Patient: %s\nHeart Rate: %.2f\nBlood Pressure: %.2f\nBody Temperature: %.2f\nOxygen Level: %.2f\nCheckup Date: %s",
                patient.getName(), heartRate, bloodPressure, bodyTemperature, oxygenLevel, checkupDate);
    }
}

class VitalsDatabase {
    private List<VitalSign> vitalsList = new ArrayList<>();

    public void addVitalSign(VitalSign vital) throws RpmsException {
        if (vital == null) {
            throw new RpmsException("INVALID_INPUT", "Vital sign cannot be null");
        }
        vitalsList.add(vital);
    }

    public void removeVitalSign(VitalSign vital) throws RpmsException {
        if (vital == null) {
            throw new RpmsException("INVALID_INPUT", "Vital sign cannot be null");
        }
        vitalsList.remove(vital);
    }

    public VitalSign getLatestVitalSign(String patientID) throws RpmsException {
        if (patientID == null || patientID.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Patient ID cannot be null or empty");
        }
        VitalSign latest = null;
        for (VitalSign v : vitalsList) {
            if (v.getPatient().getUserID().equals(patientID)) {
                if (latest == null || v.getCheckupDate().isAfter(latest.getCheckupDate())) {
                    latest = v;
                }
            }
        }
        return latest;
    }

    public List<VitalSign> getVitalSignHistory(String patientID) throws RpmsException {
        if (patientID == null || patientID.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Patient ID cannot be null or empty");
        }
        List<VitalSign> history = new ArrayList<>();
        for (VitalSign v : vitalsList) {
            if (v.getPatient().getUserID().equals(patientID)) {
                history.add(v);
            }
        }
        return history;
    }
}

// Appointment Scheduling Classes
class Appointment {
    private String appointmentID;
    private LocalDateTime appointmentTime;
    private Patient patient;
    private Doctor doctor;
    private String appointmentStatus;

    public Appointment(String appointmentID, LocalDateTime appointmentTime, Patient patient, Doctor doctor) throws RpmsException {
        if (appointmentID == null || appointmentTime == null || patient == null || doctor == null) {
            throw new RpmsException("INVALID_INPUT", "Appointment details cannot be null");
        }
        if (appointmentTime.isBefore(LocalDateTime.now())) {
            throw new RpmsException("INVALID_DATE", "Appointment time cannot be in the past");
        }
        this.appointmentID = appointmentID;
        this.appointmentTime = appointmentTime;
        this.patient = patient;
        this.doctor = doctor;
        this.appointmentStatus = "Pending";
    }

    public String getAppointmentID() { return appointmentID; }
    public LocalDateTime getAppointmentTime() { return appointmentTime; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public String getAppointmentStatus() { return appointmentStatus; }

    public void setAppointmentID(String appointmentID) throws RpmsException {
        if (appointmentID == null || appointmentID.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Appointment ID cannot be null or empty");
        }
        this.appointmentID = appointmentID;
    }

    public void setAppointmentTime(LocalDateTime appointmentTime) throws RpmsException {
        if (appointmentTime == null || appointmentTime.isBefore(LocalDateTime.now())) {
            throw new RpmsException("INVALID_DATE", "Appointment time cannot be in the past");
        }
        this.appointmentTime = appointmentTime;
    }

    public void setPatient(Patient patient) throws RpmsException {
        if (patient == null) {
            throw new RpmsException("INVALID_INPUT", "Patient cannot be null");
        }
        this.patient = patient;
    }

    public void setDoctor(Doctor doctor) throws RpmsException {
        if (doctor == null) {
            throw new RpmsException("INVALID_INPUT", "Doctor cannot be null");
        }
        this.doctor = doctor;
    }

    public void setAppointmentStatus(String appointmentStatus) throws RpmsException {
        if (appointmentStatus == null || appointmentStatus.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Appointment status cannot be null or empty");
        }
        this.appointmentStatus = appointmentStatus;
    }

    @Override
    public String toString() {
        return String.format("\nAppointment ID: %s\nAppointment Time: %s\nPatient: %s\nDoctor: %s\nStatus: %s",
                appointmentID, appointmentTime, patient.getName(), doctor.getName(), appointmentStatus);
    }
}

class AppointmentManager {
    private List<Appointment> appointments = new ArrayList<>();

    public void requestAppointment(String appointmentID, LocalDateTime time, Patient patient, Doctor doctor) throws RpmsException {
        Appointment appointment = new Appointment(appointmentID, time, patient, doctor);
        appointments.add(appointment);
        System.out.println("Appointment requested: " + appointment);
    }

    public void approveAppointment(LocalDateTime time, Patient patient, Doctor doctor) throws RpmsException {
        for (Appointment a : appointments) {
            if (a.getAppointmentTime().equals(time) && a.getPatient().equals(patient) && a.getDoctor().equals(doctor)) {
                a.setAppointmentStatus("Approved");
                System.out.println("Appointment approved: " + a);
                return;
            }
        }
        throw new RpmsException("APPOINTMENT_NOT_FOUND", "Appointment not found");
    }

    public void cancelAppointment(LocalDateTime time, Patient patient, Doctor doctor) throws RpmsException {
        for (Appointment a : appointments) {
            if (a.getAppointmentTime().equals(time) && a.getPatient().equals(patient) && a.getDoctor().equals(doctor)) {
                a.setAppointmentStatus("Cancelled");
                System.out.println("Appointment cancelled: " + a);
                return;
            }
        }
        throw new RpmsException("APPOINTMENT_NOT_FOUND", "Appointment not found");
    }

    public List<Appointment> getAppointments() {
        return new ArrayList<>(appointments);
    }
}

// Doctor-Patient Interaction Classes
class Feedback {
    private Patient patient;
    private Doctor doctor;
    private LocalDate date;
    private String comments;

    public Feedback(Patient patient, Doctor doctor, LocalDate date, String comments) throws RpmsException {
        if (patient == null || doctor == null || date == null || comments == null || comments.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Feedback details cannot be null or empty");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Feedback date cannot be in the future");
        }
        this.patient = patient;
        this.doctor = doctor;
        this.date = date;
        this.comments = comments;
    }

    public String getComments() { return comments; }
    public LocalDate getDate() { return date; }

    public void setComments(String comments) throws RpmsException {
        if (comments == null || comments.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Comments cannot be null or empty");
        }
        this.comments = comments;
    }

    public void setDate(LocalDate date) throws RpmsException {
        if (date == null || date.isAfter(LocalDate.now())) {
            throw new RpmsException("INVALID_DATE", "Feedback date cannot be in the future");
        }
        this.date = date;
    }

    @Override
    public String toString() {
        return String.format("Feedback from Dr. %s on %s: %s", doctor.getName(), date, comments);
    }
}

class Prescription {
    private String prescriptionID;
    private Patient patient;
    private String medication;
    private String dosage;
    private String schedule;

    public Prescription(String prescriptionID, Patient patient, String medication, String dosage, String schedule) throws RpmsException {
        if (prescriptionID == null || patient == null || medication == null || dosage == null || schedule == null ||
            prescriptionID.trim().isEmpty() || medication.trim().isEmpty() || dosage.trim().isEmpty() || schedule.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Prescription details cannot be null or empty");
        }
        this.prescriptionID = prescriptionID;
        this.patient = patient;
        this.medication = medication;
        this.dosage = dosage;
        this.schedule = schedule;
    }

    public String getPrescriptionID() { return prescriptionID; }
    public Patient getPatient() { return patient; }
    public String getMedication() { return medication; }
    public String getDosage() { return dosage; }
    public String getSchedule() { return schedule; }

    public void setPrescriptionID(String prescriptionID) throws RpmsException {
        if (prescriptionID == null || prescriptionID.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Prescription ID cannot be null or empty");
        }
        this.prescriptionID = prescriptionID;
    }

    public void setPatient(Patient patient) throws RpmsException {
        if (patient == null) {
            throw new RpmsException("INVALID_INPUT", "Patient cannot be null");
        }
        this.patient = patient;
    }

    public void setMedication(String medication) throws RpmsException {
        if (medication == null || medication.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Medication cannot be null or empty");
        }
        this.medication = medication;
    }

    public void setDosage(String dosage) throws RpmsException {
        if (dosage == null || dosage.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Dosage cannot be null or empty");
        }
        this.dosage = dosage;
    }

    public void setSchedule(String schedule) throws RpmsException {
        if (schedule == null || schedule.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Schedule cannot be null or empty");
        }
        this.schedule = schedule;
    }

    @Override
    public String toString() {
        return String.format("Prescription ID: %s\nPatient: %s\nMedication: %s\nDosage: %s\nSchedule: %s",
                prescriptionID, patient.getName(), medication, dosage, schedule);
    }
}

class MedicalHistory {
    private Patient patient;
    private List<String> feedbacks = new ArrayList<>();
    private List<Prescription> prescriptions = new ArrayList<>();

    public MedicalHistory(Patient patient) throws RpmsException {
        if (patient == null) {
            throw new RpmsException("INVALID_INPUT", "Patient cannot be null");
        }
        this.patient = patient;
    }

    public void addFeedback(String feedback) throws RpmsException {
        if (feedback == null || feedback.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Feedback cannot be null or empty");
        }
        feedbacks.add(feedback);
    }

    public void addPrescription(Prescription prescription) throws RpmsException {
        if (prescription == null) {
            throw new RpmsException("INVALID_INPUT", "Prescription cannot be null");
        }
        prescriptions.add(prescription);
    }

    public List<String> getFeedbacks() { return new ArrayList<>(feedbacks); }
    public List<Prescription> getPrescriptions() { return new ArrayList<>(prescriptions); }
    public Patient getPatient() { return patient; }

    @Override
    public String toString() {
        return String.format("Medical History for Patient %s:\nFeedbacks: %s\nPrescriptions: %s",
                patient.getName(), feedbacks, prescriptions);
    }
}

// Notifications and Reminders Classes
interface Notifiable {
    void sendNotification(String to, String subject, String message) throws RpmsException;
}

class EmailNotification implements Notifiable {
    private final Session mailSession;
    private final String from;
    private final String smtpHost;
    private final String smtpPort;

    public EmailNotification(String from, String password, String smtpHost, String smtpPort) throws RpmsException {
        if (from == null || password == null || smtpHost == null || smtpPort == null ||
            from.trim().isEmpty() || password.trim().isEmpty() || smtpHost.trim().isEmpty() || smtpPort.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Email notification credentials cannot be null or empty");
        }
        this.from = from;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        this.mailSession = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });
    }

    @Override
    public void sendNotification(String to, String subject, String message) throws RpmsException {
        if (to == null || subject == null || message == null || to.trim().isEmpty() || subject.trim().isEmpty() || message.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Email fields cannot be null or empty");
        }
        try {
            Message msg = new MimeMessage(mailSession);
            msg.setFrom(new InternetAddress(from));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject(subject);
            msg.setText(message);
            Transport.send(msg);
            System.out.println("Email sent to " + to + ": " + message);
        } catch (MessagingException e) {
            throw new RpmsException("EMAIL_ERROR", "Failed to send email", e);
        }
    }
}

class SMSNotification implements Notifiable {
    @Override
    public void sendNotification(String to, String subject, String message) throws RpmsException {
        if (to == null || message == null || to.trim().isEmpty() || message.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "SMS fields cannot be null or empty");
        }
        System.out.println("SMS sent to " + to + ": " + message);
    }
}

class NotificationService {
    private final Notifiable emailNotifier;
    private final Notifiable smsNotifier;

    public NotificationService(Notifiable emailNotifier, Notifiable smsNotifier) throws RpmsException {
        if (emailNotifier == null || smsNotifier == null) {
            throw new RpmsException("INVALID_INPUT", "Notifiers cannot be null");
        }
        this.emailNotifier = emailNotifier;
        this.smsNotifier = smsNotifier;
    }

    public void sendEmailAlert(String email, String subject, String message) throws RpmsException {
        emailNotifier.sendNotification(email, subject, message);
    }

    public void sendSMSAlert(String phone, String subject, String message) throws RpmsException {
        smsNotifier.sendNotification(phone, subject, message);
    }
}

class ReminderService {
    private List<Appointment> appointments = new ArrayList<>();
    private List<Prescription> prescriptions = new ArrayList<>();
    private NotificationService notificationService;

    public ReminderService(NotificationService notificationService) throws RpmsException {
        if (notificationService == null) {
            throw new RpmsException("INVALID_INPUT", "Notification service cannot be null");
        }
        this.notificationService = notificationService;
    }

    public void addAppointment(Appointment appointment) throws RpmsException {
        if (appointment == null) {
            throw new RpmsException("INVALID_INPUT", "Appointment cannot be null");
        }
        appointments.add(appointment);
    }

    public void addPrescription(Prescription prescription) throws RpmsException {
        if (prescription == null) {
            throw new RpmsException("INVALID_INPUT", "Prescription cannot be null");
        }
        prescriptions.add(prescription);
    }

    public void sendAppointmentReminder() throws RpmsException {
        for (Appointment a : appointments) {
            if ("Approved".equals(a.getAppointmentStatus())) {
                String message = "Reminder: Appointment with Dr. " + a.getDoctor().getName() +
                        " on " + a.getAppointmentTime();
                notificationService.sendEmailAlert(
                    a.getPatient().getContactInfo(),
                    "Appointment Reminder",
                    message
                );
            }
        }
    }

    public void sendMedicationReminder() throws RpmsException {
        for (Prescription p : prescriptions) {
            String message = "Reminder: Take " + p.getMedication() + " (" + p.getDosage() +
                    ") as per schedule: " + p.getSchedule();
            notificationService.sendEmailAlert(
                p.getPatient().getContactInfo(),
                "Medication Reminder",
                message
            );
        }
    }
}

// Emergency Alert System Classes
interface Alertable {
    void triggerAlert(String message) throws RpmsException;
}

class AlertService {
    private NotificationService notificationService;
    private List<String> recipients;

    public AlertService(NotificationService notificationService, List<String> recipients) throws RpmsException {
        if (notificationService == null || recipients == null || recipients.isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Notification service or recipients cannot be null or empty");
        }
        this.notificationService = notificationService;
        this.recipients = new ArrayList<>(recipients);
    }

    public void sendAlert(String message) throws RpmsException {
        for (String recipient : recipients) {
            notificationService.sendEmailAlert(recipient, "Emergency Alert", message);
        }
    }
}

abstract class EmergencyAlert implements Alertable {
    protected VitalSign vital;
    protected AlertService alertService;
    protected Patient patient;

    public EmergencyAlert(Patient patient, VitalSign vital, AlertService alertService) throws RpmsException {
        if (patient == null || alertService == null) {
            throw new RpmsException("INVALID_INPUT", "Patient or alert service cannot be null");
        }
        this.patient = patient;
        this.vital = vital;
        this.alertService = alertService;
    }

    public static boolean isWithinThreshold(VitalSign vital) {
        if (vital == null) return true;
        return (vital.getHeartRate() >= 60 && vital.getHeartRate() <= 100) &&
               (vital.getBloodPressure() >= 90 && vital.getBloodPressure() <= 140) &&
               (vital.getBodyTemperature() >= 36.1 && vital.getBodyTemperature() <= 37.2) &&
               (vital.getOxygenLevel() >= 95);
    }

    public void checkVitals() throws RpmsException {
        if (vital == null || patient == null) {
            throw new RpmsException("INVALID_VITAL", "Vital or patient information missing");
        }
        if (!isWithinThreshold(vital)) {
            String message = "Alert! Patient " + patient.getUserID() + "'s vital signs are abnormal: " +
                    "HR=" + vital.getHeartRate() + ", BP=" + vital.getBloodPressure() +
                    ", Temp=" + vital.getBodyTemperature() + ", O2=" + vital.getOxygenLevel();
            triggerAlert(message);
        }
    }
}

class VitalAlert extends EmergencyAlert {
    public VitalAlert(Patient patient, VitalSign vital, AlertService alertService) throws RpmsException {
        super(patient, vital, alertService);
    }

    @Override
    public void triggerAlert(String message) throws RpmsException {
        alertService.sendAlert(message);
    }
}

class PanicButton implements Alertable {
    private Patient patient;
    private Doctor doctor;
    private AlertService alertService;

    public PanicButton(Patient patient, Doctor doctor, AlertService alertService) throws RpmsException {
        if (patient == null || doctor == null || alertService == null) {
            throw new RpmsException("INVALID_INPUT", "Patient, doctor, or alert service cannot be null");
        }
        this.patient = patient;
        this.doctor = doctor;
        this.alertService = alertService;
    }

    @Override
    public void triggerAlert(String message) throws RpmsException {
        message = "Emergency! Patient " + patient.getUserID() + " needs immediate attention.";
        alertService.sendAlert(message);
        doctor.receiveAlert(message);
    }

    public void pressPanicButton() throws RpmsException {
        triggerAlert("");
    }
}

// Chat and Video Consultation Classes
class ChatMessage {
    private String senderId;
    private String receiverId;
    private String content;
    private LocalDateTime timestamp;
    private boolean read;

    public ChatMessage(String senderId, String receiverId, String content) throws RpmsException {
        if (senderId == null || receiverId == null || content == null ||
            senderId.trim().isEmpty() || receiverId.trim().isEmpty() || content.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "Chat message details cannot be null or empty");
        }
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.read = false;
    }

    public String getSenderId() { return senderId; }
    public String getReceiverId() { return receiverId; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public boolean isRead() { return read; }
    public void markAsRead() { this.read = true; }

    @Override
    public String toString() {
        return String.format("\nSender ID: %s\nReceiver ID: %s\nContent: %s\n", senderId, receiverId, content);
    }
}

interface ChatService {
    void sendMessage(ChatMessage message) throws RpmsException;
    List<ChatMessage> getMessagesBetween(String user1, String user2) throws RpmsException;
    List<ChatMessage> getUnreadMessages(String userId) throws RpmsException;
}

class ChatServer implements ChatService {
    private Map<String, List<ChatMessage>> messageHistory = new HashMap<>();

    @Override
    public synchronized void sendMessage(ChatMessage message) throws RpmsException {
        if (message == null) {
            throw new RpmsException("INVALID_INPUT", "Chat message cannot be null");
        }
        String key = getConversationKey(message.getSenderId(), message.getReceiverId());
        messageHistory.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
    }

    @Override
    public synchronized List<ChatMessage> getMessagesBetween(String user1, String user2) throws RpmsException {
        if (user1 == null || user2 == null || user1.trim().isEmpty() || user2.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "User IDs cannot be null or empty");
        }
        String key = getConversationKey(user1, user2);
        return new ArrayList<>(messageHistory.getOrDefault(key, new ArrayList<>()));
    }

    @Override
    public synchronized List<ChatMessage> getUnreadMessages(String userId) throws RpmsException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "User ID cannot be null or empty");
        }
        List<ChatMessage> unread = new ArrayList<>();
        for (List<ChatMessage> conversation : messageHistory.values()) {
            for (ChatMessage msg : conversation) {
                if (msg.getReceiverId().equals(userId) && !msg.isRead()) {
                    unread.add(msg);
                    msg.markAsRead();
                }
            }
        }
        return unread;
    }

    private String getConversationKey(String user1, String user2) {
        return user1.compareTo(user2) < 0 ? user1 + "-" + user2 : user2 + "-" + user1;
    }
}

class ChatClient implements Runnable {
    private String userId;
    private ChatService service;
    private boolean active;
    private Thread messageListener;

    public ChatClient(String userId, ChatService service) throws RpmsException {
        if (userId == null || service == null || userId.trim().isEmpty()) {
            throw new RpmsException("INVALID_INPUT", "User ID or chat service cannot be null");
        }
        this.userId = userId;
        this.service = service;
        this.active = true;
        this.messageListener = new Thread(this);
        this.messageListener.start();
    }

    public void sendMessage(String receiverId, String message) throws RpmsException {
        ChatMessage msg = new ChatMessage(userId, receiverId, message);
        service.sendMessage(msg);
    }

    public List<ChatMessage> getUnreadMessages() throws RpmsException {
        return service.getUnreadMessages(userId);
    }

    public void viewChatHistory(String otherUserId) throws RpmsException {
        List<ChatMessage> history = service.getMessagesBetween(userId, otherUserId);
        if (history.isEmpty()) {
            System.out.println("No messages found.");
            return;
        }
        System.out.println("\nCHAT HISTORY WITH " + otherUserId + ":");
        for (ChatMessage msg : history) {
            String prefix = msg.getSenderId().equals(userId) ? "You: " : "Them: ";
            System.out.println(prefix + msg.getContent() + " (" + msg.getTimestamp() + ")");
        }
    }

    public void stop() {
        this.active = false;
    }

    @Override
    public void run() {
        while (active) {
            try {
                List<ChatMessage> newMessages = getUnreadMessages();
                if (!newMessages.isEmpty()) {
                    System.out.println("\n=== NEW MESSAGES ===");
                    for (ChatMessage msg : newMessages) {
                        System.out.println("From " + msg.getSenderId() + ": " +
                                msg.getContent() + " (" + msg.getTimestamp() + ")");
                    }
                    System.out.println("===================");
                }
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                System.out.println("Message listener interrupted");
            } catch (RpmsException e) {
                System.out.println("Error in chat client: " + e.getMessage());
            }
        }
    }
}

interface VideoCallService {
    String generateMeetingLink() throws RpmsException;
    String startCall() throws RpmsException;
}

class VideoCall implements VideoCallService {
    private String meetingLink;
    private Doctor doctor;
    private Patient patient;

    public VideoCall(Doctor doctor, Patient patient) throws RpmsException {
        if (doctor == null || patient == null) {
            throw new RpmsException("INVALID_INPUT", "Doctor or patient cannot be null");
        }
        this.doctor = doctor;
        this.patient = patient;
        this.meetingLink = "https://rpms-video.example.com/" + UUID.randomUUID().toString();
    }

    @Override
    public String generateMeetingLink() throws RpmsException {
        if (meetingLink == null) {
            throw new RpmsException("MEETING_LINK_ERROR", "Meeting link not generated");
        }
        return meetingLink;
    }

    @Override
    public String startCall() throws RpmsException {
        if (meetingLink == null) {
            throw new RpmsException("MEETING_LINK_ERROR", "Meeting link not generated");
        }
        return "Video call started: " + meetingLink;
    }
}

// Main Application Class
public class Rpms {
    private static final Logger LOGGER = Logger.getLogger(Rpms.class.getName());
    private List<Patient> patients = new ArrayList<>();
    private List<Doctor> doctors = new ArrayList<>();
    private List<Administrator> admins = new ArrayList<>();
    private VitalsDatabase vitalsDB = new VitalsDatabase();
    private AppointmentManager appointmentManager = new AppointmentManager();
    private ChatServer chatServer = new ChatServer();
    private NotificationService notificationService;
    private Scanner sc = new Scanner(System.in);
    private User currentUser;
    private String smtpUsername;
    private String smtpPassword;

    public Rpms() {
        try {
            Notifiable emailNotifier = new EmailNotification(
                System.getenv("EMAIL_USERNAME") != null ? System.getenv("EMAIL_USERNAME") : "default@example.com",
                System.getenv("EMAIL_PASSWORD") != null ? System.getenv("EMAIL_PASSWORD") : "password",
                "smtp.gmail.com",
                "587"
            );
            Notifiable smsNotifier = new SMSNotification();
            this.notificationService = new NotificationService(emailNotifier, smsNotifier);
        } catch (RpmsException e) {
            LOGGER.severe("Failed to initialize notification service: " + e.getMessage());
        }
    }

    public void run() {
        System.out.println("--- Welcome to RPMS ---");
        while (true) {
            System.out.println("\n1. Register\n2. Login\n3. Exit");
            System.out.print("Enter your choice: ");
            try {
                int choice = Integer.parseInt(sc.nextLine());
                switch (choice) {
                    case 1: register(); break;
                    case 2: if (login()) handleUserMenu(); break;
                    case 3: System.out.println("Exiting system. Goodbye!"); return;
                    default: System.out.println("Invalid choice.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private void register() {
        System.out.println("\n--- Register ---");
        System.out.println("1. Patient\n2. Doctor\n3. Administrator");
        System.out.print("Enter your choice: ");
        try {
            int role = Integer.parseInt(sc.nextLine());
            System.out.print("Enter User ID: ");
            String id = sc.nextLine();
            System.out.print("Enter Name: ");
            String name = sc.nextLine();
            System.out.print("Enter Contact Info (Email): ");
            String contact = sc.nextLine();
            System.out.print("Enter Gender: ");
            String gender = sc.nextLine();
            if (role == 1) {
                System.out.print("Enter Birth Date (YYYY-MM-DD): ");
                LocalDate birthDate = LocalDate.parse(sc.nextLine());
                Patient patient = new Patient(id, name, contact, gender, birthDate);
                patient.setChatClient(new ChatClient(id, chatServer));
                patients.add(patient);
                System.out.println("Patient registered.");
            } else if (role == 2) {
                System.out.print("Enter Joining Date (YYYY-MM-DD): ");
                LocalDate joiningDate = LocalDate.parse(sc.nextLine());
                Doctor doctor = new Doctor(id, name, contact, gender, joiningDate);
                doctors.add(doctor);
                System.out.println("Doctor registered.");
            } else if (role == 3) {
                System.out.print("Enter Joining Date (YYYY-MM-DD): ");
                LocalDate joiningDate = LocalDate.parse(sc.nextLine());
                Administrator admin = new Administrator(id, name, contact, gender, joiningDate);
                admins.add(admin);
                System.out.println("Administrator registered.");
            } else {
                System.out.println("Invalid role.");
            }
        } catch (RpmsException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: Invalid input - " + e.getMessage());
        }
    }

    private boolean login() {
        System.out.print("Enter User ID: ");
        String id = sc.nextLine();
        for (Patient p : patients) {
            if (p.getUserID().equals(id)) {
                currentUser = p;
                configureSmtpCredentials();
                return true;
            }
        }
        for (Doctor d : doctors) {
            if (d.getUserID().equals(id)) {
                currentUser = d;
                configureSmtpCredentials();
                return true;
            }
        }
        for (Administrator a : admins) {
            if (a.getUserID().equals(id)) {
                currentUser = a;
                configureSmtpCredentials();
                return true;
            }
        }
        System.out.println("User not found.");
        return false;
    }

    private void configureSmtpCredentials() {
        if (System.getenv("EMAIL_USERNAME") == null || System.getenv("EMAIL_PASSWORD") == null) {
            System.out.println("SMTP credentials not found in environment variables.");
            System.out.print("Enter SMTP Username (e.g., Gmail address): ");
            smtpUsername = sc.nextLine();
            System.out.print("Enter SMTP Password (e.g., App Password): ");
            smtpPassword = sc.nextLine();
            try {
                Notifiable emailNotifier = new EmailNotification(smtpUsername, smtpPassword, "smtp.gmail.com", "587");
                this.notificationService = new NotificationService(emailNotifier, new SMSNotification());
            } catch (RpmsException e) {
                System.out.println("Error initializing notification service: " + e.getMessage());
            }
        } else {
            smtpUsername = System.getenv("EMAIL_USERNAME");
            smtpPassword = System.getenv("EMAIL_PASSWORD");
        }
    }

    private void handleUserMenu() {
        if (currentUser instanceof Patient) {
            patientMenu();
        } else if (currentUser instanceof Doctor) {
            doctorMenu();
        } else if (currentUser instanceof Administrator) {
            adminMenu();
        }
    }

    private void patientMenu() {
        while (true) {
            System.out.println("\n--- Patient Menu ---");
            System.out.println("1. Upload Vitals\n2. View Feedback\n3. Schedule Appointment\n4. Start Chat\n5. Trigger Panic Button\n6. Logout");
            System.out.print("Enter your choice: ");
            try {
                int choice = Integer.parseInt(sc.nextLine());
                Patient p = (Patient) currentUser;
                switch (choice) {
                    case 1:
                        System.out.print("Heart Rate: ");
                        double hr = Double.parseDouble(sc.nextLine());
                        System.out.print("Blood Pressure: ");
                        double bp = Double.parseDouble(sc.nextLine());
                        System.out.print("Body Temperature: ");
                        double temp = Double.parseDouble(sc.nextLine());
                        System.out.print("Oxygen Level: ");
                        double o2 = Double.parseDouble(sc.nextLine());
                        VitalSign vital = new VitalSign(p, hr, bp, temp, o2, LocalDate.now());
                        p.uploadVitals(vital, vitalsDB);
                        List<String> recipients = doctors.stream()
                                .map(Doctor::getContactInfo)
                                .collect(Collectors.toList());
                        VitalAlert alert = new VitalAlert(p, vital, new AlertService(notificationService, recipients));
                        alert.checkVitals();
                        break;
                    case 2:
                        p.viewFeedback();
                        break;
                    case 3:
                        System.out.print("Appointment ID: ");
                        String apptId = sc.nextLine();
                        System.out.print("Date and Time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime time = LocalDateTime.parse(sc.nextLine());
                        System.out.print("Doctor ID: ");
                        String docId = sc.nextLine();
                        Doctor doc = doctors.stream().filter(d -> d.getUserID().equals(docId)).findFirst().orElse(null);
                        if (doc != null) {
                            p.scheduleAppointment(apptId, time, doc, appointmentManager);
                        } else {
                            System.out.println("Doctor not found.");
                        }
                        break;
                    case 4:
                        System.out.print("Receiver ID: ");
                        String receiverId = sc.nextLine();
                        System.out.print("Message: ");
                        String message = sc.nextLine();
                        p.sendMessage(receiverId, message);
                        break;
                    case 5:
                        Doctor assignedDoc = doctors.isEmpty() ? null : doctors.get(0);
                        if (assignedDoc != null) {
                            PanicButton panic = new PanicButton(p, assignedDoc, new AlertService(notificationService, List.of(assignedDoc.getContactInfo())));
                            panic.pressPanicButton();
                        } else {
                            System.out.println("No doctors available to assign.");
                        }
                        break;
                    case 6:
                        return;
                    default:
                        System.out.println("Invalid choice.");
                }
            } catch (RpmsException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: Invalid input - " + e.getMessage());
            }
        }
    }

    private void doctorMenu() {
        while (true) {
            System.out.println("\n--- Doctor Menu ---");
            System.out.println("1. View Patient Vitals\n2. Provide Feedback\n3. Manage Appointment\n4. Start Video Call\n5. Send Reminders\n6. Issue Prescription\n7. Logout");
            System.out.print("Enter your choice: ");
            try {
                int choice = Integer.parseInt(sc.nextLine());
                Doctor d = (Doctor) currentUser;
                switch (choice) {
                    case 1:
                        System.out.print("Patient ID: ");
                        String patientId = sc.nextLine();
                        d.viewPatientVitals(patientId, vitalsDB);
                        break;
                    case 2:
                        System.out.print("Patient ID: ");
                        patientId = sc.nextLine();
                        Patient p = patients.stream().filter(pt -> pt.getUserID().equals(patientId)).findFirst().orElse(null);
                        if (p != null) {
                            System.out.print("Feedback: ");
                            String feedback = sc.nextLine();
                            d.provideFeedback(feedback, p.getMedicalHistory(), notificationService);
                        } else {
                            System.out.println("Patient not found.");
                        }
                        break;
                    case 3:
                        System.out.print("Patient ID: ");
                        patientId = sc.nextLine();
                        p = patients.stream().filter(pt -> pt.getUserID().equals(patientId)).findFirst().orElse(null);
                        if (p != null) {
                            System.out.print("Date and Time (YYYY-MM-DDTHH:MM): ");
                            LocalDateTime time = LocalDateTime.parse(sc.nextLine());
                            System.out.print("Action (approve/cancel): ");
                            String action = sc.nextLine();
                            d.manageAppointment(time, p, appointmentManager, action, notificationService);
                        } else {
                            System.out.println("Patient not found.");
                        }
                        break;
                    case 4:
                        System.out.print("Patient ID: ");
                        patientId = sc.nextLine();
                        p = patients.stream().filter(pt -> pt.getUserID().equals(patientId)).findFirst().orElse(null);
                        if (p != null) {
                            VideoCall call = new VideoCall(d, p);
                            System.out.println(call.startCall());
                        } else {
                            System.out.println("Patient not found.");
                        }
                        break;
                    case 5:
                        ReminderService reminders = new ReminderService(notificationService);
                        appointmentManager.getAppointments().forEach(appointment -> {
                         try {
                        reminders.addAppointment(appointment);
                        } catch (RpmsException e) {
                        System.out.println("Error adding appointment to reminders: " + e.getMessage());
                      }
                    });
    reminders.sendAppointmentReminder();
    break;
                    case 6:
                        System.out.print("Patient ID: ");
                        patientId = sc.nextLine();
                        p = patients.stream().filter(pt -> pt.getUserID().equals(patientId)).findFirst().orElse(null);
                        if (p != null) {
                            System.out.print("Prescription ID: ");
                            String prescriptionId = sc.nextLine();
                            System.out.print("Medication: ");
                            String medication = sc.nextLine();
                            System.out.print("Dosage: ");
                            String dosage = sc.nextLine();
                            System.out.print("Schedule: ");
                            String schedule = sc.nextLine();
                            Prescription prescription = new Prescription(prescriptionId, p, medication, dosage, schedule);
                            p.getMedicalHistory().addPrescription(prescription);
                            System.out.println("Prescription issued: " + prescription);
                        } else {
                            System.out.println("Patient not found.");
                        }
                        break;
                    case 7:
                        return;
                    default:
                        System.out.println("Invalid choice.");
                }
            } catch (RpmsException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: Invalid input - " + e.getMessage());
            }
        }
    }

    private void adminMenu() {
        while (true) {
            System.out.println("\n--- Administrator Menu ---");
            System.out.println("1. Add Patient\n2. Add Doctor\n3. Remove Patient\n4. Remove Doctor\n5. View Logs\n6. Logout");
            System.out.print("Enter your choice: ");
            try {
                int choice = Integer.parseInt(sc.nextLine());
                Administrator a = (Administrator) currentUser;
                switch (choice) {
                    case 1:
                        System.out.print("Patient ID: ");
                        String id = sc.nextLine();
                        System.out.print("Name: ");
                        String name = sc.nextLine();
                        System.out.print("Contact Info (Email): ");
                        String contact = sc.nextLine();
                        System.out.print("Gender: ");
                        String gender = sc.nextLine();
                        System.out.print("Birth Date (YYYY-MM-DD): ");
                        LocalDate birthDate = LocalDate.parse(sc.nextLine());
                        Patient p = new Patient(id, name, contact, gender, birthDate);
                        p.setChatClient(new ChatClient(id, chatServer));
                        a.addPatient(p);
                        patients.add(p);
                        break;
                    case 2:
                        System.out.print("Doctor ID: ");
                        id = sc.nextLine();
                        System.out.print("Name: ");
                        name = sc.nextLine();
                        System.out.print("Contact Info (Email): ");
                        contact = sc.nextLine();
                        System.out.print("Gender: ");
                        gender = sc.nextLine();
                        System.out.print("Joining Date (YYYY-MM-DD): ");
                        LocalDate joiningDate = LocalDate.parse(sc.nextLine());
                        Doctor d = new Doctor(id, name, contact, gender, joiningDate);
                        a.addDoctor(d);
                        doctors.add(d);
                        break;
                    case 3:
                        System.out.print("Patient ID: ");
                        id = sc.nextLine();
                        p = patients.stream().filter(pt -> pt.getUserID().equals(id)).findFirst().orElse(null);
                        if (p != null) {
                            a.removePatient(p);
                            patients.remove(p);
                        } else {
                            System.out.println("Patient not found.");
                        }
                        break;
                    case 4:
                        System.out.print("Doctor ID: ");
                        id = sc.nextLine();
                        d = doctors.stream().filter(doc -> doc.getUserID().equals(id)).findFirst().orElse(null);
                        if (d != null) {
                            a.removeDoctor(d);
                            doctors.remove(d);
                        } else {
                            System.out.println("Doctor not found.");
                        }
                        break;
                    case 5:
                        a.getSystemLogs().forEach(System.out::println);
                        break;
                    case 6:
                        return;
                    default:
                        System.out.println("Invalid choice.");
                }
            } catch (RpmsException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Error: Invalid input - " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new Rpms().run();
    }
}