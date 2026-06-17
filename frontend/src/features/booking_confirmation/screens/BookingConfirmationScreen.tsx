"use client";

import React, { useState, useMemo } from "react";
import { Download, Search, Receipt } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
export type BookingConfirmation = {
  id: string;
  bookingNo: string;
  guestName: string;
  roomType: string;
  checkIn: string;
  checkOut: string;
  amount: number;
  status: string;
};

export function BookingConfirmationScreen() {
  const [bookings, setBookings] = useState<BookingConfirmation[]>([]);
  const [search, setSearch] = useState("");

  const filteredBookings = useMemo(() => {
    return bookings.filter(
      b =>
        b.bookingNo.toLowerCase().includes(search.toLowerCase()) ||
        b.guestName.toLowerCase().includes(search.toLowerCase()) ||
        b.roomType.toLowerCase().includes(search.toLowerCase())
    );
  }, [bookings, search]);

  const handleDownload = (bNum: string) => {
    alert(`Downloading PDF Contract/Confirmation slip for reservation ${bNum}...`);
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Booking Confirmations</h1>
          <p className="text-xs text-slate-400">Generate PDF invoices, track guest verification sign-offs, and final counts</p>
        </div>
        <Badge variant="success" className="text-xs px-2.5 font-bold bg-emerald-100 text-emerald-800">
          {bookings.length} Finalized Bookings
        </Badge>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search confirmation #, guest name, room type..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>
        </CardContent>
      </Card>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Booking Number</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Guest Name</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Room Type</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Check In</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Check Out</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Total Price</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Booking Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Contracts</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredBookings.length > 0 ? (
              filteredBookings.map(booking => (
                <TableRow key={booking.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <span className="flex items-center gap-1.5 text-blue-600">
                      <Receipt className="size-3.5 text-slate-400" />
                      {booking.bookingNo}
                    </span>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-700">{booking.guestName}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600">{booking.roomType}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500">{booking.checkIn}</TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-500">{booking.checkOut}</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-black text-slate-800">
                    ${booking.amount.toLocaleString('en-US')}
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge variant={booking.status === "confirmed" ? "success" : "warning"} size="sm" className="font-bold text-[9px] uppercase">
                      {booking.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    <div className="flex justify-center items-center gap-1.5">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleDownload(booking.bookingNo)}
                        className="px-2 py-1 text-[10px] border-slate-200 text-slate-600 hover:bg-slate-50 transition"
                      >
                        <Download className="size-3 mr-1 inline" /> PDF
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={8} className="py-8 text-center text-slate-400 text-xs">
                  No booking confirmations matched your criteria.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
